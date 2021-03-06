package com.roadrover.sdk.audio;

import android.content.Context;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.SparseIntArray;

import com.roadrover.sdk.BaseManager;
import com.roadrover.services.audio.IAudio;
import com.roadrover.services.audio.IAudioCallback;
import com.roadrover.sdk.utils.Logcat;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * IVI 音频管理
 */
public class IVIAudioManager extends BaseManager {
    private IAudio mAudioInterface;
    private AudioListener mAudioListener;
    private VolumeBarListener mVolumeBarListener;
    private SparseIntArray mValueCaches = new SparseIntArray();

    /**
     * 音频监听
     */
    public interface AudioListener {
        /**
         * 音量发生变化
         * @param id {@link com.roadrover.sdk.audio.IVIAudio.Channel}
         * @param value 音量值
         */
        void onVolumeChanged(int id, int value);

        /**
         * 静音状态发生变化
         * @param mute true静音，false不在静音
         * @param source
         */
        void onMuteChanged(boolean mute, int source);
    }

    /**
     * 音量条接口，该回调只有SystemUi需要关心
     */
    public interface VolumeBarListener {
        /**
         * 显示音量条，或者改变当前音量条的属性，比如从主音量变成了蓝牙音量
         * @param id AudioParam.Id
         * @param value 当前音量值
         * @param maxValue 当前音量的最大值
         */
        void onShowVolumeBar(int id, int value, int maxValue);

        /**
         * 隐藏音量条
         */
        void onHideVolumeBar();
    }

    /**
     * 构造函数
     * @param context          上下文
     * @param connectListener  连接监听对象
     * @param audioListener    音频监听对象
     */
    public IVIAudioManager(Context context, ConnectListener connectListener, AudioListener audioListener) {
        super(context, connectListener, true);
        mAudioListener = audioListener;
    }

    @Override
    public void disconnect() {
        mAudioCallback = null;
        mAudioInterface = null;
        mAudioListener = null;
        mVolumeBarListener = null;
        mValueCaches = null;
        super.disconnect();
    }

    @Override
    protected String getServiceActionName() {
        return ServiceAction.AUDIO_ACTION;
    }

    @Override
    protected void onServiceConnected(IBinder service) {
        mAudioInterface = IAudio.Stub.asInterface(service);
        if (mAudioCallback != null) {
            registerCallback(mAudioCallback);
        }
    }

    @Override
    protected void onServiceDisconnected() {
        mAudioInterface = null;
    }

    @Override
    protected void registerCallback(IInterface callback) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.registerCallback((IAudioCallback) callback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        super.registerCallback(callback);
    }

    @Override
    protected void unRegisterCallback(IInterface callback) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.unRegisterCallback((IAudioCallback) callback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        super.unRegisterCallback(callback);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVolumeChanged(IVIAudio.EventVolumeChanged event) {
        if (mAudioListener != null) {
            mAudioListener.onVolumeChanged(event.mId, event.mValue);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMuteChanged(IVIAudio.EventMuteChanged event) {
        if (mAudioListener != null) {
            mAudioListener.onMuteChanged(event.mMute, event.mSource);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVolumeBar(IVIAudio.EventVolumeBar event) {
        if (mVolumeBarListener != null) {
            if (event.mId == AudioParam.Id.NONE) {
                mVolumeBarListener.onHideVolumeBar();
            } else {
                mVolumeBarListener.onShowVolumeBar(event.mId, event.mValue, event.mMaxValue);
            }
        }
    }

    /**
     * 音频回调对象
     */
    private IAudioCallback mAudioCallback = new IAudioCallback.Stub() {
        @Override
        public void onVolumeChanged(int id, int value) {
            Logcat.d(AudioParam.Id.getName(id) + " : " + value);
            if (updateCache(id, value)) {
                post(new IVIAudio.EventVolumeChanged(id, value));
            }
        }

        @Override
        public void onMuteChanged(boolean mute, int source) {
            if (updateCache(AudioParam.Id.MUTE, mute ? 1 : 0)){
                post(new IVIAudio.EventMuteChanged(mute, source));
            }
        }

        @Override
        public void onVolumeBar(int id, int value, int maxValue) {
            Logcat.d(AudioParam.Id.getName(id) + " value: " + value + " max value: " + maxValue);
            post(new IVIAudio.EventVolumeBar(id, value, maxValue));
        }

        @Override
        public void onSecondaryMuteChanged(boolean mute) {
            if (updateCache(AudioParam.Id.MUTE_SECONDARY, mute ? 1 : 0)){
                post(new IVIAudio.EventSecondaryMuteChanged(mute));
            }
        }
    };

    /**
     * 判断这个参数是否有效，指定id是否存在
     * @param id {@link com.roadrover.sdk.audio.AudioParam.Id}
     * @return 返回参数是否有效
     */
    public boolean isParamAvailable(int id) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.isParamAvailable(id);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return false;
    }

    /**
     * 获取指定id的参数的最小值，例：主音量
     * @param id {@link com.roadrover.sdk.audio.AudioParam.Id}
     * @return 返回指定参数的最小值
     */
    public int getParamMinValue(int id) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getParamMinValue(id);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0;
    }

    /**
     * 获取指定id的参数的最大值，例：主音量
     * @param id {@link com.roadrover.sdk.audio.AudioParam.Id}
     * @return 返回指定参数的最大值
     */
    public int getParamMaxValue(int id) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getParamMaxValue(id);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0;
    }

    /**
     * 获取指定id的参数的默认值，例：主音量
     * @param id {@link com.roadrover.sdk.audio.AudioParam.Id}
     * @return 返回指定参数的默认值
     */
    public int getParamDefaultValue(int id) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getParamDefaultValue(id);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0;
    }

    /**
     * 获取指定id的参数的当前值，例：主音量
     * @param id {@link com.roadrover.sdk.audio.AudioParam.Id}
     * @return 指定id参数的值
     */
    public int getParamValue(int id) {
        if (mAudioInterface != null) {
            try {
                int value = mAudioInterface.getParam(id);
                updateCache(id, value);
                return value;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0;
    }

    /**
     * 设置指定id的参数，例：设置主音量 setParam(AudioParam.Id.VOLUME_MASTER, 10);
     * @param id 音量id {@link com.roadrover.sdk.audio.AudioParam.Id}
     * @param value 音量值
     */
    public void setParam(int id, int value) {
        if (!updateCache(id, value)) {
            return;
        }

        if (mAudioInterface != null) {
            try {
                mAudioInterface.setParam(id, value);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 设置一个声音参数
     * @param param {@link AudioParam}
     */
    public void setParam(AudioParam param) {
        if (param == null) {
            return;
        }
        setParam(param.mId, param.mValue);
    }

    /**
     * 设置一个默认的声音参数
     * @param param {@link AudioParam}
     */
    public void setDefaultParam(AudioParam param) {
        if (param == null) {
            return;
        }
        setParam(param.mId, param.mDefault);
    }

    /**
     * 获取一个声音参数setBalanceFade
     * @param id {@link com.roadrover.sdk.audio.AudioParam.Id}
     * @return 整个音频的参数，例：主音量，{@link AudioParam}
     */
    public AudioParam getParam(int id) {
        if (mAudioInterface != null) {
            try {
                if (!mAudioInterface.isParamAvailable(id)) {
                    return null;
                }

                int minValue = mAudioInterface.getParamMinValue(id);
                int maxValue = mAudioInterface.getParamMaxValue(id);
                int defaultValue = mAudioInterface.getParamDefaultValue(id);
                int value = mAudioInterface.getParam(id);

                AudioParam param = new AudioParam(id, minValue, maxValue, defaultValue);
                param.mValue = value;
                return param;

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return null;
    }

    /**
     * 判断这个参数是否有效，指定channel是否存在
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回参数是否有效
     */
    public boolean isBuildInPreVolumeAvailable(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.isBuildInPreVolumeAvailable(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return false;
    }

    /**
     * 获取指定channel的参数的最小值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回指定参数的最小值
     */
    public float getBuildInPreVolumeMinValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getBuildInPreVolumeMinValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 获取指定channel的参数的最大值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回指定参数的最大值
     */
    public float getBuildInPreVolumeMaxValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getBuildInPreVolumeMaxValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 获取指定channel的参数的默认值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回指定参数的默认值
     */
    public float getBuildInPreVolumeDefaultValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getBuildInPreVolumeDefaultValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 获取指定channel的参数的当前值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 指定channel参数的值
     */
    public float getBuildInPreVolumeValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getBuildInPreVolumeValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 设置指定channel的参数，例：设置PC setBuildInPreVolume(IVIAudio.Channel.PC, 4.65);
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @param value   增益数值
     */
    public void setBuildInPreVolume(int channel, float value) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.setBuildInPreVolumeValue(channel, value);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 重设参数
     */
    public void resetBuildInPreVolume() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.resetBuildInPreVolumeValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 设置一个声音参数
     * @param param {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     */
    public void setBuildInPreVolume(IVIAudio.Channel param) {
        if (param == null) {
            return;
        }
        setBuildInPreVolume(param.mId, param.mValue);
    }

    /**
     * 设置一个默认的声音参数
     * @param param {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     */
    public void setDefaultBuildInPreVolume(IVIAudio.Channel param) {
        if (param == null) {
            return;
        }
        setBuildInPreVolume(param.mId, param.mDefault);
    }

    /**
     * 获取一个声音参数
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 整个音频的参数，例：PC
     */
    public IVIAudio.Channel getBuildInPreVolume(int channel) {
        if (mAudioInterface != null) {
            try {
                if (!mAudioInterface.isBuildInPreVolumeAvailable(channel)) {
                    return null;
                }

                float minValue = mAudioInterface.getBuildInPreVolumeMinValue(channel);
                float maxValue = mAudioInterface.getBuildInPreVolumeMaxValue(channel);
                float defaultValue = mAudioInterface.getBuildInPreVolumeDefaultValue(channel);
                float value = mAudioInterface.getBuildInPreVolumeValue(channel);

                IVIAudio.Channel param = new IVIAudio.Channel(channel, minValue, maxValue, defaultValue);
                param.mValue = value;
                return param;

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return null;
    }

    /**
     * 判断这个参数是否有效，指定channel是否存在
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回参数是否有效
     */
    public boolean isSecondaryBuildInPreVolumeAvailable(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.isSecondaryBuildInPreVolumeAvailable(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return false;
    }

    /**
     * 获取指定channel的参数的最小值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回指定参数的最小值
     */
    public float getSecondaryBuildInPreVolumeMinValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryBuildInPreVolumeMinValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 获取指定channel的参数的最大值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回指定参数的最大值
     */
    public float getSecondaryBuildInPreVolumeMaxValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryBuildInPreVolumeMaxValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 获取指定channel的参数的默认值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 返回指定参数的默认值
     */
    public float getSecondaryBuildInPreVolumeDefaultValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryBuildInPreVolumeDefaultValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 获取指定channel的参数的当前值，例：PC
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 指定channel参数的值
     */
    public float getSecondaryBuildInPreVolumeValue(int channel) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryBuildInPreVolumeValue(channel);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return 0.0f;
    }

    /**
     * 设置指定channel的参数，例：设置PC setBuildInPreVolume(IVIAudio.Channel.PC, 4.65);
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @param value   增益值
     */
    public void setSecondaryBuildInPreVolume(int channel, float value) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.setSecondaryBuildInPreVolumeValue(channel, value);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 重设参数
     */
    public void resetSecondaryBuildInPreVolume() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.resetSecondaryBuildInPreVolumeValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 设置一个声音参数
     * @param param {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     */
    public void setSecondaryBuildInPreVolume(IVIAudio.Channel param) {
        if (param == null) {
            return;
        }
        setSecondaryBuildInPreVolume(param.mId, param.mValue);
    }

    /**
     * 设置一个默认的声音参数
     * @param param {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     */
    public void setDefaultSecondaryBuildInPreVolume(IVIAudio.Channel param) {
        if (param == null) {
            return;
        }
        setSecondaryBuildInPreVolume(param.mId, param.mDefault);
    }

    /**
     * 获取一个声音参数
     * @param channel {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     * @return 整个音频的参数，例：PC，见{@link com.roadrover.sdk.audio.IVIAudio.Channel}
     */
    public IVIAudio.Channel getSecondaryBuildInPreVolume(int channel) {
        if (mAudioInterface != null) {
            try {
                if (!mAudioInterface.isSecondaryBuildInPreVolumeAvailable(channel)) {
                    return null;
                }

                float minValue = mAudioInterface.getSecondaryBuildInPreVolumeMinValue(channel);
                float maxValue = mAudioInterface.getSecondaryBuildInPreVolumeMaxValue(channel);
                float defaultValue = mAudioInterface.getSecondaryBuildInPreVolumeDefaultValue(channel);
                float value = mAudioInterface.getSecondaryBuildInPreVolumeValue(channel);

                IVIAudio.Channel param = new IVIAudio.Channel(channel, minValue, maxValue, defaultValue);
                param.mValue = value;
                return param;

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return null;
    }

    /**
     * 开发者接口，判断前置音频设备是否有效
     * @return 前置音频设备是否有效
     */
    public boolean isMasterAudioDeviceAvailable() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.isMasterAudioDeviceAvailable();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return false;
    }

    /**
     * 开发者接口，获取音量增益的最小值
     * @return 音量增益的最小值
     */
    public float getMasterVolumeGainMinValue() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getMasterVolumeGainMinValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，获取音量增益的最大值
     * @return 音量增益的最大值
     */
    public float getMasterVolumeGainMaxValue() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getMasterVolumeGainMaxValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，获取音量增益的默认值
     * @param volume 音量值
     * @return 音量增益的默认值
     */
    public float getMasterVolumeGainDefaultValue(int volume) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getMasterVolumeGainDefaultValue(volume);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，获取音量增益的值
     * @param volume 音量值
     * @return 音量增益的值
     */
    public float getMasterVolumeGainValue(int volume) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getMasterVolumeGainValue(volume);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，设置音量增益的值
     * @param volume 音量值
     * @param value 音量增益值
     */
    public void setMasterVolumeGainValue(int volume, float value) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.setMasterVolumeGainValue(volume, value);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 开发者接口，重置所有音量增益的值
     */
    public void resetMasterVolumeGainValue() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.resetMasterVolumeGainValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 开发者接口，判断辅助音频设备是否有效
     * @return 辅助音频设备而是否有效
     */
    public boolean isSecondaryAudioDeviceAvailable() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.isSecondaryAudioDeviceAvailable();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return false;
    }

    /**
     * 开发者接口，获取辅助音量增益的最小值
     * @return 辅助音量增益的最小值
     */
    public float getSecondaryVolumeGainMinValue() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryVolumeGainMinValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，获取辅助音量增益的最大值
     * @return 辅助音量增益的最大值
     */
    public float getSecondaryVolumeGainMaxValue() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryVolumeGainMaxValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，获取辅助音量增益的默认值
     * @param volume 音量值
     * @return 辅助音量增益的默认值
     */
    public float getSecondaryVolumeGainDefaultValue(int volume) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryVolumeGainDefaultValue(volume);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，获取辅助音量增益的值
     * @param volume 音量值
     * @return 辅助音量增益的值
     */
    public float getSecondaryVolumeGainValue(int volume) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryVolumeGainValue(volume);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0.0f;
    }

    /**
     * 开发者接口，设置辅助音音量增益的值
     * @param volume 音量值
     * @param value 辅助音量增益的值
     */
    public void setSecondaryVolumeGainValue(int volume, float value) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.setSecondaryVolumeGainValue(volume, value);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 开发者接口，重置所有辅助音量增益的值
     */
    public void resetSecondaryVolumeGainValue() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.resetSecondaryVolumeGainValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
    }

    /**
     * 获取前置最小音量
     * @return 前置最小音量
     */
    public int getMasterVolumeMin() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getMasterVolumeMin();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0;
    }

    /**
     * 获取前置最大音量
     * @return 前置最大音量
     */
    public int getMasterVolumeMax() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getMasterVolumeMax();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0;
    }

    /**
     * 获取辅助最小音量
     * @return 辅助最小音量
     */
    public int getSecondaryVolumeMin() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryVolumeMin();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0;
    }

    /**
     * 获取辅助最大音量
     * @return 辅助最大音量
     */
    public int getSecondaryVolumeMax() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getSecondaryVolumeMax();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }
        return 0;
    }

    /**
     * 获取音效参数
     * @param eqMode {@link com.roadrover.sdk.audio.AudioParam.EqMode}
     * @return 返回指定eq模式的参数数组，例：流行等
     */
    public int[] getEqGains(int eqMode) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getEqGains(eqMode);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.d("Service not connected");
        }

        return null;
    }

    /**
     * 显示音量条
     */
    public void showVolumeBar() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.showVolumeBar();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 显示次通道音量条，乘客区
     */
    public void showSecondaryVolumeBar() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.showSecondaryVolumeBar();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取当前激活的音量ID
     */
    public int getActiveVolumeId() {
        int ret = AudioParam.Id.VOLUME_MASTER;
        if (mAudioInterface != null) {
            try {
                ret = mAudioInterface.getActiveVolumeId();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    /**
     * 隐藏音量条
     */
    public void hideVolumeBar() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.hideVolumeBar();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 如果音量条是显示的，则隐藏，反之显示
     */
    public void toggleVolumeBar() {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.toggleVolumeBar();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 设置音量条显示和隐藏的监听
     * @param listener 音量条监听对象，见{@link VolumeBarListener}
     */
    public void setVolumeBarListener(VolumeBarListener listener){
        mVolumeBarListener = listener;
    }

    /**
     * 更新缓存
     * @param id
     * @param value
     * @return
     */
    protected boolean updateCache(int id, int value) {
        if (mValueCaches == null) {
            return false;
        }
        if (mValueCaches.indexOfKey(id) < 0 || value != mValueCaches.get(id)) {
            mValueCaches.put(id, value);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 请求内部先静音，隔一段时候反静音
     * @param muteDurationMs 静音后反静音时间ms
     */
    public void requestInternalShortMute(int muteDurationMs) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.requestInternalShortMute(muteDurationMs);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.e("service not connected");
        }
    }

    /**
     * 在收音机、AV、TV、AUX、BC05的A2DP等场景下，有了导航提示音，此时的媒体音量应该下降，
     * 调用此函数来设置媒体音量的百分比
     * @param percent 100最大，0没有
     */
    public void setAnalogMediaVolumePercent(int percent) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.setAnalogMediaVolumePercent(percent);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.e("service not connected");
        }
    }

    /**
     * 获取当前主音频通道
     * @return {@link com.roadrover.sdk.audio.IVIAudio.Channel}
     */
    public int getMasterAudioChannel() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getMasterAudioChannel();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.e("service not connected");
        }

        return IVIAudio.Channel.PC;
    }

    /**
     * 设置音频DSP的参数，专家音效畅友使用
     */
    public void setChipParam(int chipId, int paramId, double v0, double v1, double v2, double v3) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.setChipParam(chipId, paramId, v0, v1, v2, v3);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.e("service not connected");
        }
    }

    /**
     * 添加专家音效配置文件，将机器内部的音效文件替换成pathName文件内的内容
     * @param effect AudioParam.ExpertAudioEffect
     * @param pathName 音效文件路径全名
     * @param apply 是否立即起作用
     */
    public void addExpertAudioEffect(int effect, String pathName, boolean apply) {
        if (mAudioInterface != null) {
            try {
                mAudioInterface.addExpertAudioEffect(effect, pathName, apply);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.e("service not connected");
        }
    }

    /**
     * 获取当前支持的专家音效场景列表
     * @return 数组内部存放 AudioParam.ExpertAudioEffect
     */
    public int[] getAvailableExpertAudioEffects() {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getAvailableExpertAudioEffects();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.e("service not connected");
        }

        return new int[0];
    }

    /**
     * 获取专家音效文件名，如果不存在，则返回null
     * @param effect AudioParam.ExpertAudioEffect
     * @return 文件路径全名
     */
    public String getExpertAudioEffectFile(int effect) {
        if (mAudioInterface != null) {
            try {
                return mAudioInterface.getExpertAudioEffectFile(effect);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            Logcat.e("service not connected");
        }

        return null;
    }
}
