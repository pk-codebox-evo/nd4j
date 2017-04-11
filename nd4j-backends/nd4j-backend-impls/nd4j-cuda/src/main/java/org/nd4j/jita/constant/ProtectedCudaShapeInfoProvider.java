package org.nd4j.jita.constant;

import lombok.extern.slf4j.Slf4j;
import org.nd4j.jita.allocator.impl.AtomicAllocator;
import org.nd4j.jita.conf.Configuration;
import org.nd4j.jita.conf.CudaEnvironment;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.BaseShapeInfoProvider;
import org.nd4j.linalg.api.shape.ShapeDescriptor;
import org.nd4j.linalg.factory.Nd4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author raver119@gmail.com
 */
@Slf4j
public class ProtectedCudaShapeInfoProvider extends BaseShapeInfoProvider {

    private AtomicAllocator allocator;

    private AtomicLong cacheHit = new AtomicLong(1);
    private AtomicLong cacheMiss = new AtomicLong(1);

    private Semaphore lock = new Semaphore(1);

    protected static final ConstantProtector protector = ConstantProtector.getInstance();

    private static ProtectedCudaShapeInfoProvider ourInstance = new ProtectedCudaShapeInfoProvider();


    private ProtectedCudaShapeInfoProvider() {

    }

    /**
     * This method forces cache purge, if cache is available for specific implementation
     */
    @Override
    public void purgeCache() {
        protector.purgeProtector();
    }

    public static ProtectedCudaShapeInfoProvider getInstance() {
        return ourInstance;
    }

    @Override
    public DataBuffer createShapeInformation(int[] shape, int[] stride, int offset, int elementWiseStride, char order) {
        // We enforce offset to 0 in shapeBuffer, since we need it for cache efficiency + we don't actually use offset value @ native side
        offset = 0;

        Integer deviceId = AtomicAllocator.getInstance().getDeviceId();

        ShapeDescriptor descriptor = new ShapeDescriptor(shape, stride, offset, elementWiseStride, order);

        if (!protector.containsDataBuffer(deviceId, descriptor)) {
            //log.info("Cache miss: {}", descriptor);
            DataBuffer buffer = super.createShapeInformation(shape, stride, offset, elementWiseStride, order);
            buffer.setConstant(true);

            if (CudaEnvironment.getInstance().getConfiguration().getMemoryModel() == Configuration.MemoryModel.IMMEDIATE) {
                Nd4j.getConstantHandler().moveToConstantSpace(buffer);
            }

            //deviceCache.get(deviceId).put(descriptor, buffer);
            protector.persistDataBuffer(deviceId, descriptor, buffer);

            cacheMiss.incrementAndGet();
            return buffer;
        } else {
            //       log.info("Cache hit: {}", descriptor);
            cacheHit.incrementAndGet();
        }

        return protector.getDataBuffer(deviceId, descriptor); //deviceCache.get(deviceId).get(descriptor);
    }
}
