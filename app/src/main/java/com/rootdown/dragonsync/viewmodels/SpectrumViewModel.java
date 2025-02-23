// java/com.rootdown.dragonsync/viewmodels/SpectrumViewModel.java
package com.rootdown.dragonsync.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.rootdown.dragonsync.network.MulticastHandler;
import com.rootdown.dragonsync.utils.Settings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpectrumViewModel extends ViewModel {
    private static final int SUSCAN_REMOTE_FRAGMENT_HEADER_MAGIC = 0xABCD0123;
    private static final byte SUSCAN_ANALYZER_SUPERFRAME_TYPE_PSD = 0x02;

    private final MutableLiveData<List<SpectrumData>> spectrumData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isListening = new MutableLiveData<>(false);
    private final MutableLiveData<String> connectionError = new MutableLiveData<>();

    private MulticastHandler multicastHandler;
    private final Map<Byte, FragmentData> fragmentBuffer = new HashMap<>();

    public static class SpectrumData {
        public final int fc;              // Center frequency
        public final double timestamp;
        public final float sampleRate;    // Sample rate in Hz
        public final float[] data;        // FFT data points

        public SpectrumData(int fc, double timestamp, float sampleRate, float[] data) {
            this.fc = fc;
            this.timestamp = timestamp;
            this.sampleRate = sampleRate;
            this.data = data;
        }
    }

    private static class FragmentData {
        public final double timestamp;
        public final ByteBuffer data;

        public FragmentData(double timestamp, int size) {
            this.timestamp = timestamp;
            this.data = ByteBuffer.allocate(size);
        }
    }

    public void startListening(int port) {
//        if (isListening.getValue()) return;
//
//        multicastHandler = new MulticastHandler();
//        multicastHandler.startListening(
//                Settings.getInstance(getApplication()).getMulticastHost(),
//                port,
//                new MulticastHandler.MessageHandler() {
//                    @Override
//                    public void onMessage(byte[] data) {
//                        processFragment(data);
//                    }
//
//                    @Override
//                    public void onError(String error) {
//                        connectionError.postValue(error);
//                    }
//                }
//        );
//
//        isListening.setValue(true);
    }

    public void stopListening() {
        if (multicastHandler != null) {
            multicastHandler.stopListening();
            multicastHandler = null;
        }
        isListening.setValue(false);
        connectionError.setValue(null);
        fragmentBuffer.clear();
    }

    private void processFragment(byte[] data) {
//        if (data.length < RemoteHeader.SIZE) return;
//
//        RemoteHeader header = new RemoteHeader(data);
//        if (header.magic != SUSCAN_REMOTE_FRAGMENT_HEADER_MAGIC) return;
//        if (header.sfType != SUSCAN_ANALYZER_SUPERFRAME_TYPE_PSD) return;
//
//        byte[] payload = Arrays.copyOfRange(data, RemoteHeader.SIZE, data.length);
//
//        FragmentData fragment = fragmentBuffer.computeIfAbsent(
//                header.sfId,
//                id -> new FragmentData(System.currentTimeMillis() / 1000.0, header.sfSize)
//        );
//
//        fragment.data.put(payload);
//
//        if (fragment.data.position() >= header.sfSize) {
//            processCompleteFragment(fragment);
//            fragmentBuffer.remove(header.sfId);
//        }
    }

    private void processCompleteFragment(FragmentData fragment) {
//        fragment.data.flip();
//        float[] spectralData = new float[fragment.data.remaining() / 4];
//        fragment.data.asFloatBuffer().get(spectralData);
//
//        // First float is center freq, second is sample rate, rest is FFT data
//        SpectrumData spectrum = new SpectrumData(
//                (int)spectralData[0],
//                fragment.timestamp,
//                spectralData[1],
//                Arrays.copyOfRange(spectralData, 2, spectralData.length)
//        );
//
//        List<SpectrumData> currentData = spectrumData.getValue();
//        currentData.add(spectrum);
//        if (currentData.size() > 100) {
//            currentData.remove(0);
//        }
//        spectrumData.postValue(currentData);
    }

    public LiveData<List<SpectrumData>> getSpectrumData() {
        return spectrumData;
    }

    public LiveData<Boolean> getIsListening() {
        return isListening;
    }

    public LiveData<String> getConnectionError() {
        return connectionError;
    }

    private static class RemoteHeader {
        public static final int SIZE = 16;

        public final int magic;      // 0xABCD0123
        public final byte sfType;    // Type 0x02 for PSD
        public final short size;     // Fragment size
        public final byte sfId;      // Fragment ID
        public final int sfSize;     // Total data size
        public final int sfOffset;   // Offset in data

        public RemoteHeader(byte[] data) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            magic = buffer.getInt();
            sfType = buffer.get();
            size = buffer.getShort();
            sfId = buffer.get();
            sfSize = buffer.getInt();
            sfOffset = buffer.getInt();
        }
    }
}