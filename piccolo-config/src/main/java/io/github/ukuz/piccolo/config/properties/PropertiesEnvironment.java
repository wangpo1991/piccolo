/*
 * Copyright 2019 ukuz90
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.ukuz.piccolo.config.properties;


import io.github.ukuz.piccolo.api.annotation.AnnotationTypeFilter;
import io.github.ukuz.piccolo.api.common.ClassPathScanner;
import io.github.ukuz.piccolo.api.common.Holder;
import io.github.ukuz.piccolo.api.config.ConfigurationProperties;
import io.github.ukuz.piccolo.api.config.Environment;
import io.github.ukuz.piccolo.api.config.EnvironmentException;
import io.github.ukuz.piccolo.api.config.Properties;
import io.github.ukuz.piccolo.api.external.common.utils.ClassUtils;
import io.github.ukuz.piccolo.config.common.ConfigurationPropertiesProcessor;
import io.netty.util.internal.StringUtil;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author ukuz90
 */
public class PropertiesEnvironment implements Environment {

    private final ConfigurationPropertiesProcessor processor;

    private final Logger logger = LoggerFactory.getLogger(PropertiesEnvironment.class);

    private final ConcurrentMap<String, Properties> configMap = new ConcurrentHashMap<>();
    private final AtomicBoolean isScan = new AtomicBoolean();

    private static final String RESOURCE_SEPARATOR = "/";
    private static final String CONF_DIR_NAME = "conf";
    private static final String CONF_FILE_NAME = CONF_DIR_NAME + RESOURCE_SEPARATOR + "piccolo.properties";
    private static final Properties EMPTY = new EmptyProperties();

    private Holder<Configuration> config = new Holder<>();

    public PropertiesEnvironment() {
        this.processor = new PropertiesConfigurationPropertiesProcessor();

        this.processor.init();
    }

    @Override
    public void scanAllProperties() {
        /**
         * 防止多次扫描
         */
        if (!isScan.compareAndSet(false, true)) {
            throw new EnvironmentException("repeat scan properties");
        }

        logger.info("scanAllProperties begin.");
        // 只扫描@ConfigurationProperties的类
        ClassPathScanner scanner = new ClassPathScanner();
        AnnotationTypeFilter filter = new AnnotationTypeFilter(ConfigurationProperties.class);
        scanner.addIncludeFilter(filter);

        try {
            Set<Class> classes = scanner.scan(new String[]{"io.github.ukuz.piccolo"});
            logger.info("scanAllProperties, scan class: " + classes);
            /**
             * 找到所有的类并过滤出Properties类型
             */
            classes.parallelStream()
                    .filter(Properties.class::isAssignableFrom)
                    .forEach(this::newInstance);
        } catch (Exception e) {
            throw new EnvironmentException(e);
        }

    }

    /**
     * 初始化配置类的容器configMap
     * @param clazz
     */
    private void newInstance(Class<?> clazz) {
        try {
            Properties properties = (Properties) clazz.newInstance();
            configMap.putIfAbsent(clazz.getName(), properties);
        } catch (InstantiationException e) {
            logger.error("new instance failure, cause: {}", e.getMessage());
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            logger.error("new instance failure, cause: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void load(String configFileName) throws EnvironmentException {
        if (configFileName.startsWith("/") || configFileName.indexOf(":") == 1) {
            File configFile = new File(configFileName);
            if (!configFile.exists()) {
                throw new IllegalArgumentException("load config file failure, file not found : " + configFileName);
            }
        } else {
            if (!StringUtil.isNullOrEmpty(configFileName)) {
                configFileName = CONF_DIR_NAME + RESOURCE_SEPARATOR + configFileName;
            } else {
                configFileName = CONF_FILE_NAME;
            }
            URL url = findClassLoader().getResource(configFileName);
            if (url == null) {
                throw new IllegalArgumentException("load config file failure, file not found : " + configFileName);
            }
        }
        logger.info("load config : {}", configFileName);

        try {
            if (config.getValue() == null) {
                synchronized (config) {
                    if (config.getValue() == null) {
                        PropertiesConfigurations configs = new PropertiesConfigurations();
                        Configuration c = configs.create(configFileName);
                        //根据configMap中的配置类进行注入配置文件的数据
                        configMap.values()
                                .forEach(properties -> processor.process(c, properties));
                        config.setValue(c);
                    }
                }
            }
        } catch (ConfigurationException e) {
            throw new EnvironmentException(e);
        }
    }

    @Override
    public <T extends Properties> T getProperties(Class<T> clazz) {
        if (configMap.containsKey(clazz.getName())) {
            return (T) configMap.get(clazz.getName());
        }
        lazyInitialize(clazz);
        return (T) configMap.getOrDefault(clazz.getName(), EMPTY);
    }

    /**
     * 延迟加载的配置类
     * @param clazz
     * @param <T>
     */
    private <T extends Properties> void lazyInitialize(Class<T> clazz) {
        logger.info("lazyInitialize class: " + clazz.getName() + " begin.");
        if (!clazz.isAnnotationPresent(ConfigurationProperties.class)) {
            throw new IllegalArgumentException(clazz.getName() + " must annotated with @ConfigurationProperties");
        }
        if (!Properties.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(clazz.getName() + " must implements Properties");
        }
        try {
            Properties properties = clazz.newInstance();
            processor.process(config.getValue(), properties);
            configMap.putIfAbsent(clazz.getName(), properties);
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        logger.info("lazyInitialize class: " + clazz.getName() + " finish.");
    }

    private ClassLoader findClassLoader() {
        return ClassUtils.getClassLoader(PropertiesEnvironment.class);
    }

    public static class EmptyProperties implements Properties {}
}
