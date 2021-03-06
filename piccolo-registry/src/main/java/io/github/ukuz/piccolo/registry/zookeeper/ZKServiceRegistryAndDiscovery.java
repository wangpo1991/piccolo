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
package io.github.ukuz.piccolo.registry.zookeeper;

import io.github.ukuz.piccolo.api.PiccoloContext;
import io.github.ukuz.piccolo.api.external.common.Assert;
import io.github.ukuz.piccolo.api.service.AbstractService;
import io.github.ukuz.piccolo.api.service.ServiceException;
import io.github.ukuz.piccolo.api.service.ServiceRegistryAndDiscovery;
import io.github.ukuz.piccolo.api.service.discovery.DefaultServiceInstance;
import io.github.ukuz.piccolo.api.service.discovery.ServiceListener;
import io.github.ukuz.piccolo.common.json.Jsons;
import io.github.ukuz.piccolo.registry.NoAvailableServiceException;
import io.github.ukuz.piccolo.registry.zookeeper.listener.ZooKeeperCacheListener;
import io.github.ukuz.piccolo.registry.zookeeper.manager.ZooKeeperManager;
import io.github.ukuz.piccolo.registry.zookeeper.properties.ZooKeeperProperties;
import org.apache.curator.utils.ZKPaths;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author ukuz90
 */
public class ZKServiceRegistryAndDiscovery extends AbstractService implements ServiceRegistryAndDiscovery<DefaultServiceInstance> {

    private ZooKeeperManager zkManager;

    @Override
    public void init(PiccoloContext context) throws ServiceException {
        ZooKeeperProperties prop = context.getProperties(ZooKeeperProperties.class);
        zkManager = new ZooKeeperManager(prop, WATCH_PATH);
        zkManager.init();
    }

    @Override
    protected CompletableFuture<Boolean> doStartAsync() {
        zkManager.start();
        CompletableFuture future = new CompletableFuture<>();
        future.complete(true);
        return future;
    }

    @Override
    public void destroy() throws ServiceException {
        zkManager.destroy();
    }

    @Override
    public List<DefaultServiceInstance> lookup(String serviceId) {
        Assert.notEmptyString(serviceId, "serviceId must not empty");
        List<String> childrenKeys = zkManager.getDirectory().getChildrenKeys(serviceId);
        if (childrenKeys.isEmpty()) {
            throw new NoAvailableServiceException("can not found any available service " + serviceId);
        }
        return childrenKeys.stream()
                .map(key -> serviceId + ZKPaths.PATH_SEPARATOR + key)
                .map(zkManager.getDirectory()::getData)
                .filter(Objects::nonNull)
                .map(childData -> Jsons.fromJson(childData, DefaultServiceInstance.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void subscribe(String serviceId, ServiceListener<DefaultServiceInstance> listener) {
        Assert.notEmptyString(serviceId, "serviceId must not empty");
        Assert.notNull(listener, "listener must not be null");
        zkManager.getDirectory().registerListener(new ZooKeeperCacheListener(serviceId, listener));
    }

    @Override
    public void unsubcribe(String serviceId, ServiceListener<DefaultServiceInstance> listener) {
        Assert.notEmptyString(serviceId, "serviceId must not empty");
        Assert.notNull(listener, "listener must not be null");
        zkManager.getDirectory().unregisterListenr(new ZooKeeperCacheListener(serviceId, listener));
    }

    @Override
    public void registry(DefaultServiceInstance registration) {
        Assert.notNull(registration, "registration must not be null");
        if (registration.isPersistent()) {
            zkManager.getDirectory().registerPersistNode(registration.getServicePath(), Jsons.toJson(registration));
        } else {
            zkManager.getDirectory().registerEphemeralNode(registration.getServicePath(), Jsons.toJson(registration));
        }
    }

    @Override
    public void deregistry(DefaultServiceInstance registration) {
        Assert.notNull(registration, "registration must not be null");
        zkManager.getDirectory().removePath(registration.getServicePath());
    }

}
