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

package io.github.ukuz.piccolo.transport.codec;

import io.github.ukuz.piccolo.api.connection.Connection;
import io.github.ukuz.piccolo.api.exchange.protocol.Packet;
import io.github.ukuz.piccolo.api.exchange.support.MultiMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiPacketCodecTest {

    private MultiPacketCodec codec;

    @Mock
    private Connection connection;

    @BeforeEach
    void setUp() {
        codec = new MultiPacketCodec();
    }

    @DisplayName("test_decode_WithNotIntact")
    @Test
    void testDecodeWithNotIntact() {
        ByteBuf out = PooledByteBufAllocator.DEFAULT.heapBuffer();
        out.writeByte(0xbc);
        out.writeByte(0xc0);
        out.writeByte(1);
        out.writeByte(0);
        out.writeInt(1);
        out.writeByte(0);
        out.writeInt(1);

        MultiMessage msg = (MultiMessage) codec.decode(connection, out);
        assertNull(msg);
    }

    @DisplayName("test_decode_WithHugePacket")
    @Test
    void testDecodeWithHugePacket() {
        ByteBuf out = PooledByteBufAllocator.DEFAULT.heapBuffer();
        out.writeByte(0xbc);
        out.writeByte(0xc0);
        out.writeByte(1);
        out.writeByte(0);
        out.writeInt(1);
        out.writeByte(0);
        out.writeInt(10 * 1024 * 1024 + 1);

        try {
            codec.decode(connection, out);
            fail();
        } catch (CodecException e) {
            assertEquals(PacketSizeLimitCodecException.class, e.getClass());
        }
    }

    @DisplayName("test_decode_WithUnknownPacket")
    @Test
    void testDecodeWithUnknownPacket() {
        ByteBuf out = PooledByteBufAllocator.DEFAULT.heapBuffer();
        out.writeByte(0xc0);
        out.writeByte(0xbc);
        out.writeByte(1);
        out.writeByte(0);
        out.writeInt(1);
        out.writeByte(0);
        out.writeInt(0);

        try {
            codec.decode(connection, out);
            fail();
        } catch (CodecException e) {
            assertEquals(PacketUnknownCodecException.class, e.getClass());
        }
    }

    @DisplayName("test_decode")
    @Test
    void testDecode() {
        ByteBuf out = PooledByteBufAllocator.DEFAULT.heapBuffer();
        out.writeByte(0xbc);
        out.writeByte(0xc0);
        out.writeByte(1);
        out.writeByte(0);
        out.writeInt(1);
        out.writeByte(0);
        out.writeInt(12);
        out.writeBytes("Hello,World!".getBytes());

        out.writeByte(0xbc);
        out.writeByte(0xc0);
        out.writeByte(1);
        out.writeByte(0);
        out.writeInt(1);
        out.writeByte(0);
        out.writeInt(4);
        out.writeBytes("HaHa".getBytes());

        MultiMessage msg = (MultiMessage) codec.decode(connection, out);

        assertEquals(2, msg.size());
        assertEquals("Hello,World!", new String(((Packet)msg.get(0)).getPayload()));
        assertEquals("HaHa", new String(((Packet)msg.get(1)).getPayload()));
    }
}