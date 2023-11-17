package com.luoye.dpt;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteBufferTest {
    @Test
    public void testBytesToInt() {
        byte[] bytes  = {
                0x1,0x0,0x0,0x0,
                0x2,0x0,0x0,0x0,
                0x3,0x0,0x0,0x0
        };

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        assert byteBuffer.getInt(0) == 1;
        assert byteBuffer.getInt(4) == 2;
        assert byteBuffer.getInt(8) == 3;
    }
}
