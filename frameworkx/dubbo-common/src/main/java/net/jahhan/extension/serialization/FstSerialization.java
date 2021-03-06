/**
 * Copyright 1999-2014 dangdang.com.
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
package net.jahhan.extension.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.inject.Singleton;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.serialize.support.fst.FstObjectInput;
import com.alibaba.dubbo.common.serialize.support.fst.FstObjectOutput;

import net.jahhan.com.alibaba.dubbo.common.serialize.ObjectInput;
import net.jahhan.com.alibaba.dubbo.common.serialize.ObjectOutput;
import net.jahhan.common.extension.annotation.Extension;

/**
 * @author lishen
 */
@Extension("fst")
@Singleton
public class FstSerialization implements OptimizedSerialization {

	public byte getContentTypeId() {
		return 9;
	}

	public String getContentType() {
		return "x-application/fst";
	}

	public ObjectOutput serialize(URL url, OutputStream out) throws IOException {
		return new FstObjectOutput(out);
	}

	public ObjectInput deserialize(URL url, InputStream is) throws IOException {
		return new FstObjectInput(is);
	}

}