package com.example.gateway.route;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.example.gateway.config.DynamicRouteProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;

@Component
@ConditionalOnProperty(prefix = "gateway.dynamic-route", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NacosDynamicRouteRefresher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NacosDynamicRouteRefresher.class);

    private static final String ROUTES_PREFIX = "spring.cloud.gateway.routes";

    private final DynamicRouteProperties dynamicRouteProperties;

    private final NacosConfigManager nacosConfigManager;

    private final GatewayProperties gatewayProperties;

    private final ApplicationEventPublisher eventPublisher;

    private Listener listener;

    private volatile String initialConfigContent;

    public NacosDynamicRouteRefresher(DynamicRouteProperties dynamicRouteProperties,
            NacosConfigManager nacosConfigManager,
            GatewayProperties gatewayProperties,
            ApplicationEventPublisher eventPublisher) {
        this.dynamicRouteProperties = dynamicRouteProperties;
        this.nacosConfigManager = nacosConfigManager;
        this.gatewayProperties = gatewayProperties;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void registerListener() {
        ConfigService configService = nacosConfigManager.getConfigService();
        listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                refreshRoutes(configInfo, "Nacos config changed");
            }
        };

        try {
            initialConfigContent = configService.getConfigAndSignListener(
                    dynamicRouteProperties.getDataId(),
                    dynamicRouteProperties.getGroup(),
                    dynamicRouteProperties.getTimeoutMs(),
                    listener);
            log.info("Nacos dynamic route listener registered, initial config fetched");
        }
        catch (NacosException ex) {
            log.warn("Load dynamic routes from Nacos failed, keep current gateway routes. dataId={}, group={}",
                    dynamicRouteProperties.getDataId(), dynamicRouteProperties.getGroup(), ex);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initRoutes() {
        if (initialConfigContent != null) {
            refreshRoutes(initialConfigContent, "Nacos initial load");
            initialConfigContent = null;
        }
    }

    @Override
    public void destroy() {
        if (listener == null) {
            return;
        }
        nacosConfigManager.getConfigService()
                .removeListener(dynamicRouteProperties.getDataId(), dynamicRouteProperties.getGroup(), listener);
    }

    private void refreshRoutes(String content, String reason) {
        try {
            List<RouteDefinition> routes = parseRoutes(content);
            gatewayProperties.setRoutes(routes);
            eventPublisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("{} dynamic routes from Nacos. dataId={}, group={}, routeCount={}", reason,
                    dynamicRouteProperties.getDataId(), dynamicRouteProperties.getGroup(), routes.size());
        }
        catch (RuntimeException ex) {
            log.error("Parse dynamic routes from Nacos failed, keep previous gateway routes. dataId={}, group={}",
                    dynamicRouteProperties.getDataId(), dynamicRouteProperties.getGroup(), ex);
        }
    }

    private List<RouteDefinition> parseRoutes(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }

        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
        Properties properties = yamlFactory.getObject();
        if (properties == null || properties.isEmpty()) {
            return Collections.emptyList();
        }

        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        properties.forEach((key, value) -> source.put(key.toString(), value));

        return new ArrayList<>(new Binder(source)
                .bind(ROUTES_PREFIX, Bindable.listOf(RouteDefinition.class))
                .orElse(Collections.emptyList()));
    }
}
