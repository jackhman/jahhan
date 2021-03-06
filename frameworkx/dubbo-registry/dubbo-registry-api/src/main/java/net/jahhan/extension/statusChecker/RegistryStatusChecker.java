/*
 * Copyright 1999-2011 Alibaba Group.
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
package net.jahhan.extension.statusChecker;

import java.util.Collection;

import javax.inject.Singleton;

import com.alibaba.dubbo.registry.Registry;
import com.alibaba.dubbo.registry.support.AbstractRegistryFactory;
import com.frameworkx.annotation.Activate;

import net.jahhan.common.extension.annotation.Extension;
import net.jahhan.extension.statusChecker.Status;
import net.jahhan.spi.StatusChecker;

/**
 * RegistryStatusChecker
 * 
 * @author william.liangf
 */
@Activate
@Extension("registry")
@Singleton
public class RegistryStatusChecker implements StatusChecker {

    public Status check() {
        Collection<Registry> regsitries = AbstractRegistryFactory.getRegistries();
        if (regsitries == null || regsitries.size() == 0) {
            return new Status(Status.Level.UNKNOWN);
        }
        Status.Level level = Status.Level.OK;
        StringBuilder buf = new StringBuilder();
        for (Registry registry : regsitries) {
            if (buf.length() > 0) {
                buf.append(",");
            }
            buf.append(registry.getUrl().getAddress());
            if (! registry.isAvailable()) {
                level = Status.Level.ERROR;
                buf.append("(disconnected)");
            } else {
                buf.append("(connected)");
            }
        }
        return new Status(level, buf.toString());
    }

}