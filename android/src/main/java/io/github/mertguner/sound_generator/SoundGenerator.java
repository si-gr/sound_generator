package io.github.mertguner.sound_generator;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioAttributes;
import android.os.Build;

import io.github.mertguner.sound_generator.generators.sawtoothGenerator;
import io.github.mertguner.sound_generator.generators.signalDataGenerator;
import io.github.mertguner.sound_generator.generators.sinusoidalGenerator;
import io.github.mertguner.sound_generator.generators.squareWaveGenerator;
import io.github.mertguner.sound_generator.generators.triangleGenerator;
import io.github.mertguner.sound_generator.handlers.isPlayingStreamHandler;
import io.github.mertguner.sound_generator.models.WaveTypes;

@TargetApi(Build.VERSION_CODES.CUPCAKE)
public class SoundGenerator {

    private Thread bufferThread;
    private AudioTrack audioTrack;
    private signalDataGenerator generator;
    private boolean isPlaying = false;
    private int minSamplesSize;
    private WaveTypes waveType = WaveTypes.SINUSOIDAL;
    private float rightVolume = 1, leftVolume = 1;
    private boolean cleanStart = false;
    private float volume = 0.5f;

    public void setCleanStart(boolean cleanStart) {
        this.cleanStart = cleanStart;
    }

    public void setAutoUpdateOneCycleSample(boolean autoUpdateOneCycleSample) {
        if (generator != null)
            generator.setAutoUpdateOneCycleSample(autoUpdateOneCycleSample);
    }

    public int getSampleRate() {
        if (generator != null)
            return generator.getSampleRate();
        return 0;
    }

    public void setSampleRate(int sampleRate) {
        if (generator != null)
            generator.setSampleRate(sampleRate);
    }

    public void refreshOneCycleData() {
        if (generator != null)
            generator.createOneCycleData(true);
    }

    public void setFrequency(float v) {
        if (generator != null)
            generator.setFrequency(v);
    }

    public float getFrequency() {
        if (generator != null)
            return generator.getFrequency();
        return 0;
    }

    public void setBalance(float balance) {
        balance = Math.max(-1, Math.min(1, balance));

        rightVolume = (balance >= 0) ? 1 : (balance == -1) ? 0 : (1 + balance);
        leftVolume = (balance <= 0) ? 1 : (balance == 1) ? 0 : (1 - balance);
        if (audioTrack != null) {
            audioTrack.setStereoVolume(leftVolume, rightVolume);
        }
    }


    public void setVolume(float _volume) {
        this.volume = Math.max(0, Math.min(1, _volume));

        updateVolume();
    }

    private void updateVolume() {
        if (this.audioTrack != null) {
            this.audioTrack.setStereoVolume(this.leftVolume * this.volume, this.rightVolume * this.volume);
        }
    }

    public void setWaveform(WaveTypes waveType) {
        if (this.waveType.equals(waveType) || (generator == null))
            return;

        this.waveType = waveType;

        if (waveType.equals(WaveTypes.SINUSOIDAL))
            generator.setGenerator(new sinusoidalGenerator());
        else if (waveType.equals(WaveTypes.TRIANGLE))
            generator.setGenerator(new triangleGenerator());
        else if (waveType.equals(WaveTypes.SQUAREWAVE))
            generator.setGenerator(new squareWaveGenerator());
        else if (waveType.equals(WaveTypes.SAWTOOTH))
            generator.setGenerator(new sawtoothGenerator());
    }

    public boolean init(int sampleRate) {
        try {
            this.minSamplesSize = 2000;
            //sampleRate = 44100;
            this.generator = new signalDataGenerator(this.minSamplesSize, sampleRate);
            this.audioTrack = new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).build()).setBufferSizeInBytes(this.minSamplesSize).build();
            return true;
        }catch (Exception ex)
        {
            return false;
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public void startPlayback() {
        if (bufferThread != null || audioTrack == null) return;

        isPlaying = true;

        bufferThread = new Thread(new Runnable() {
            @Override
            public void run() {
                audioTrack.flush();
                audioTrack.setPlaybackHeadPosition(0);
                short[] data = generator.getData();
                audioTrack.write(data, 0, minSamplesSize);
                audioTrack.play();
                long startTime = System.nanoTime();
                boolean updatedVolume = false;
                while (isPlaying) {
                    for (long currentTime = System.nanoTime(); currentTime - startTime < 1000000L; currentTime = System.nanoTime()) {
                        if (currentTime % 1000L < 200L) {
                            if (!updatedVolume) {
                                updateVolume();
                                updatedVolume = true;
                            }
                        } else {
                            updatedVolume = false;
                        }
                    }
                    data = generator.getData();
                    audioTrack.write(data, 0, minSamplesSize);
                    startTime = System.nanoTime();

                }
            }
        }
        );

        isPlayingStreamHandler.change(true);

        bufferThread.start();
    }

    public void stopPlayback() {
        if (bufferThread == null) return;

        isPlaying = false;

        try {
            bufferThread.join(); //Waiting thread
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        isPlayingStreamHandler.change(false);
        bufferThread = null;

        if (audioTrack != null) {
            audioTrack.stop();
        }
    }

    public void release() {
        if (isPlaying())
            stopPlayback();
        audioTrack.release();
    }

}
