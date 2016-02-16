/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.atomix.vertx;

import io.atomix.catalyst.buffer.BufferInput;
import io.atomix.catalyst.buffer.BufferOutput;
import io.atomix.catalyst.serializer.Serializer;
import io.atomix.catalyst.serializer.TypeSerializer;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.impl.ClusterSerializable;

/**
 * Cluster serializable serializer.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class ClusterSerializableSerializer<T extends ClusterSerializable> implements TypeSerializer<T> {

  @Override
  public void write(T object, BufferOutput output, Serializer serializer) {
    Buffer buffer = Buffer.buffer();
    object.writeToBuffer(buffer);
    byte[] bytes = buffer.getBytes();
    output.writeUnsignedShort(bytes.length).write(bytes);
  }

  @Override
  public T read(Class<T> type, BufferInput input, Serializer serializer) {
    try {
      T object = type.newInstance();
      byte[] bytes = new byte[input.readUnsignedShort()];
      input.read(bytes);
      Buffer buffer = Buffer.buffer(bytes);
      object.readFromBuffer(0, buffer);
      return object;
    } catch (InstantiationException | IllegalAccessException e) {
      throw new VertxException("failed to instantiate serializable type: " + type);
    }
  }

}
