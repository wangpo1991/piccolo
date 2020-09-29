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

/**
 * @author ukuz90
 */
public class PiccoloMain {

    public static void main(String[] args) {
        /**
         * 程序加载器
         */
        ServerLauncher launcher = new ServerLauncher();

        /**
         * 初始化程序加载器，主要用于加载服务组件
         */
        launcher.init(args);
        /**
         * 服务加载器维护了一个双向链表，组装了程序组件，此处启动最终会启动各组件以及
         */
        launcher.start();

        registerShutdownHook(launcher);
    }

    private static void registerShutdownHook(final ServerLauncher launcher) {
        Runtime.getRuntime().addShutdownHook(new Thread(()->
            launcher.stop()
        , "piccolo-shutdown-hook-thread"));
    }

}
