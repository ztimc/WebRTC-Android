package com.sabinetek.webrtcsample;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.EglBase$$CC;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";
    private static final String VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";
    private static final String VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL = "WebRTC-IntelVP8/Enabled/";
    private static final String DISABLE_WEBRTC_AGC_FIELDTRIAL =
            "WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/";


    private PeerConnectionFactory mPeerConnectionFactory;
    private DefaultVideoEncoderFactory encoderFactory;
    private DefaultVideoDecoderFactory decoderFactory;
    private EglBase eglBase;

    private VideoCapturer mVideoCapturer;
    private MediaConstraints mAudioMediaConstraints;
    private MediaConstraints mVideoMediaConstraints;

    private VideoTrack mVideoTrack;
    private AudioTrack mAudioTrack;
    private org.webrtc.SurfaceViewRenderer mSurfaceRenderRemote;
    private org.webrtc.SurfaceViewRenderer mSurfaceRenderLocal;

    private PeerConnection mLocalPeer;
    private PeerConnection mRemotePeer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.mSurfaceRenderLocal = findViewById(R.id.surface_render_local);
        this.mSurfaceRenderRemote = findViewById(R.id.surface_render_remote);
        start();
    }


    public void start() {
        //Initialize PeerConnectionFactory globals.
        //Params are context, initAudio,initVideo anfd videoCodecHwAcceleration
        String fieldTrials = "";
        fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
        fieldTrials += VIDEO_VP8_INTEL_HW_ENCODER_FIELDTRIAL;
        fieldTrials += DISABLE_WEBRTC_AGC_FIELDTRIAL;

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(getApplicationContext())
                        .setFieldTrials(fieldTrials)
                        .setEnableVideoHwAcceleration(true)
                        .setEnableInternalTracer(true)
                        .createInitializationOptions());
        eglBase = EglBase$$CC.create$$STATIC$$();
        encoderFactory = new DefaultVideoEncoderFactory(
                eglBase.getEglBaseContext(), true /* enableIntelVp8Encoder */, true);
        decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.networkIgnoreMask = 0;

        mSurfaceRenderLocal.init(eglBase.getEglBaseContext(),null);
        mSurfaceRenderRemote.init(eglBase.getEglBaseContext(),null);

        mPeerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(createJavaAudioDevice())
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        mVideoCapturer = createVideoCapturer();
        mAudioMediaConstraints = new MediaConstraints();
        mVideoMediaConstraints = new MediaConstraints();

        mVideoTrack = createVideoTrack(mVideoCapturer);
        mAudioTrack = createAudioTrack();

        mVideoTrack.addSink(mSurfaceRenderLocal);

    }

    public void call(){
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        final MediaConstraints sdpConstraints = new MediaConstraints();
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveAudio", "true"));
        sdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"));

        mLocalPeer = mPeerConnectionFactory.createPeerConnection(iceServers,new PCObserver(){
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
            }
        });

        mRemotePeer = mPeerConnectionFactory.createPeerConnection(iceServers,new PCObserver(){
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                super.onAddStream(mediaStream);
            }
        });


        MediaStream stream = mPeerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(mAudioTrack);
        stream.addTrack(mVideoTrack);
        mLocalPeer.addStream(stream);

        mLocalPeer.createOffer(new SDPObserver(){
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                mLocalPeer.setLocalDescription(new SDPObserver(),sessionDescription);
                mRemotePeer.setRemoteDescription(new SDPObserver(),sessionDescription);
                mRemotePeer.createAnswer(new SDPObserver(){
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        mRemotePeer.setLocalDescription(new SDPObserver(),sessionDescription);
                        mLocalPeer.setRemoteDescription(new SDPObserver(),sessionDescription);
                    }
                },new MediaConstraints());
            }
        },sdpConstraints);

    }


    @Nullable
    private AudioTrack createAudioTrack() {
        AudioSource audioSource = mPeerConnectionFactory.createAudioSource(mAudioMediaConstraints);
        AudioTrack  localAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);
        return localAudioTrack;
    }

    @Nullable
    private VideoTrack createVideoTrack(VideoCapturer capturer) {
        VideoSource videoSource = mPeerConnectionFactory.createVideoSource(capturer);
        capturer.startCapture(1280, 720, 25);

        VideoTrack localVideoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        localVideoTrack.setEnabled(true);
        return localVideoTrack;
    }

    private @Nullable VideoCapturer createVideoCapturer() {
        final VideoCapturer videoCapturer;
           // videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        videoCapturer = createCameraCapturer(new Camera1Enumerator(false));
        if (videoCapturer == null) {
            reportError("Failed to open camera");
            return null;
        }
        return videoCapturer;
    }

    private @Nullable VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        AudioTrack audioTrack = stream.audioTracks.get(0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    videoTrack.addSink(mSurfaceRenderRemote);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void onIceCandidateReceived(PeerConnection peer, IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        if (peer == mLocalPeer) {
            mRemotePeer.addIceCandidate(iceCandidate);
        } else {
            mLocalPeer.addIceCandidate(iceCandidate);
        }
    }


    AudioDeviceModule createJavaAudioDevice() {

        // Set audio record error callbacks.
        JavaAudioDeviceModule.AudioRecordErrorCallback audioRecordErrorCallback = new JavaAudioDeviceModule.AudioRecordErrorCallback() {
            @Override
            public void onWebRtcAudioRecordInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordInitError: " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordStartError(
                    JavaAudioDeviceModule.AudioRecordStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordStartError: " + errorCode + ". " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioRecordError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioRecordError: " + errorMessage);
                reportError(errorMessage);
            }
        };

        JavaAudioDeviceModule.AudioTrackErrorCallback audioTrackErrorCallback = new JavaAudioDeviceModule.AudioTrackErrorCallback() {
            @Override
            public void onWebRtcAudioTrackInitError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackInitError: " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackStartError(
                    JavaAudioDeviceModule.AudioTrackStartErrorCode errorCode, String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackStartError: " + errorCode + ". " + errorMessage);
                reportError(errorMessage);
            }

            @Override
            public void onWebRtcAudioTrackError(String errorMessage) {
                Log.e(TAG, "onWebRtcAudioTrackError: " + errorMessage);
                reportError(errorMessage);
            }
        };

        return JavaAudioDeviceModule.builder(getApplicationContext())
                .setUseHardwareAcousticEchoCanceler(false)
                .setUseHardwareNoiseSuppressor(false)
                .setAudioRecordErrorCallback(audioRecordErrorCallback)
                .setAudioTrackErrorCallback(audioTrackErrorCallback)
                .createAudioDeviceModule();
    }



    private void reportError(String errorMessage) {
        Log.e(TAG, "reportError: " + errorMessage);
    }

    private class PCObserver implements PeerConnection.Observer {

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {

        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }

        @Override
        public void onTrack(RtpTransceiver rtpTransceiver) {

        }
    }

    private class SDPObserver implements SdpObserver{

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {

        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }
}
