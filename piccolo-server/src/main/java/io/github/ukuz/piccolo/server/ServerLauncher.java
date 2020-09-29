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
package io.github.ukuz.piccolo.server;

import io.github.ukuz.piccolo.core.PiccoloServer;
import io.github.ukuz.piccolo.server.boot.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * @author ukuz90
 */
public class ServerLauncher {

    private BootProcessChain processChain;
    private PiccoloServer server;
    private final Logger logger = LoggerFactory.getLogger(ServerLauncher.class);

    /**
     * 展现banner图
     */
    private BootJob lastJob = new BootJob() {
        @Override
        public void start() {
            try {
                InputStream is = ServerLauncher.class.getClassLoader().getResourceAsStream("banner.txt");
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (Exception e) {
                logger.info("server launch success!!!");
            }
        }
    };

    public ServerLauncher() {
    }
    /**
     * 初始化服务组件
     */
    public void init(String... args) {
        if (server == null) {
            /**
             * 初始化PiccoloServer
             */
            server = new PiccoloServer();
        }

        server.runWebServer(args);

        if (processChain == null) {
            processChain = newBootProcessChain();
        }
        /**
         * 注册服务
          */
        processChain.addLast(new ServiceRegistryBoot(server.getServiceRegistry(), server));
        /**
         *
         */
        processChain.addLast(new MQClientBoot(server.getMQClient(), server));
        processChain.addLast(new CacheManagerBoot(server.getCacheManager(), server));
        processChain.addLast(new ConfigCenterBoot(server.getDynamicConfiguration(), server));
        processChain.addLast(new ServerBoot(server.getGatewayServer(), true));
        processChain.addLast(new ServerBoot(server.getConnectServer()));
        processChain.addLast(new ServerBoot(server.getWebSocketServer()));
        processChain.addLast(new RouterCenterBoot(server.getRouterCenter()));
        processChain.addLast(new RouteLocatorBoot(server.getRouteLocator(), server));
        processChain.addLast(new IdGenBoot(server.getIdGen()));
        processChain.addLast(new MonitorBoot(server.getMonitor(), server));
        processChain.addLast(lastJob);
    }

    void start() {
        processChain.start();
    }

    void stop() {
        processChain.stop();
    }

    private BootProcessChain newBootProcessChain() {
        return new DefaultBootProcessChain();
    }

}
