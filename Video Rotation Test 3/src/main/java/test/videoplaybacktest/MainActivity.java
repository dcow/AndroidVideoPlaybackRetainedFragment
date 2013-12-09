package test.videoplaybacktest;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.MediaController;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getActionBar().setSubtitle("[TextureView & retained Fragment]");

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        private static final String TAG = PlaceholderFragment.class.getSimpleName();

        public PlaceholderFragment() {
        }

        View mLoading;
        TextureView mDisplay;
        View mBackdrop;
        MediaController mMediaController;

        SurfaceTexture mTexture;

        boolean mResumed;
        boolean mTextureReady;
        boolean mPrepared;

        MediaPlayer mMediaPlayer;

        int mAmountBuffered;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);

        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            mLoading = rootView.findViewById(R.id.loading);
            mBackdrop = rootView.findViewById(R.id.backdrop);
            mDisplay = (TextureView) rootView.findViewById(R.id.texture_view);
            if (mTexture != null) {
                mDisplay.setSurfaceTexture(mTexture);
            }
            mDisplay.setSurfaceTextureListener(mTextureListener);

            return rootView;
        }

        @Override
        public void onResume() {
            super.onResume();
            mResumed = true;

            if (mTextureReady) {
                if (mMediaPlayer == null) {
                    mLoading.setVisibility(View.VISIBLE);
                    initMedia();
                } else {
                    setViewBounds(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight(), mDisplay, mBackdrop);
                    initController();
                }
            }
        }

        void initMedia() {
            mMediaPlayer = new MediaPlayer();
            try {
                mMediaPlayer.setDataSource("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4");
                mMediaPlayer.setSurface(new Surface(mTexture));
                mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                    @Override
                    public void onBufferingUpdate(MediaPlayer mp, int percent) {
                        mAmountBuffered = percent;
                    }
                });
                mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mPrepared = true;
                        mDisplay.post(new Runnable() {
                            @Override
                            public void run() {
                                setViewBounds(mMediaPlayer.getVideoWidth(), mMediaPlayer.getVideoHeight(), mDisplay, mBackdrop);
                                initController();
                                mLoading.setVisibility(View.INVISIBLE);
                                mMediaController.show();
                            }
                        });
                    }
                });
                mMediaPlayer.prepareAsync();
            } catch (IOException ioe) {
                Log.e(TAG, "Can't open video stream.", ioe);
                throw new RuntimeException("Can't open video stream.");
            }
        }

        void destroyMedia() {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mPrepared = false;
        }

        TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mTexture = surface;
                mTextureReady = true;

                if (mResumed) {
                    if (mMediaPlayer == null) {
                        initMedia();
                    } else {
                        initController();
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                mTexture = surface;
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mTexture = surface;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                mTexture = surface;
            }
        };

        void initController() {
            final Activity a = getActivity();
            mMediaController = new MediaController(a);
            mMediaController.setAnchorView(a.findViewById(android.R.id.content));
            mMediaController.setMediaPlayer(mControllerBridge);
            mMediaController.setEnabled(true);
            mDisplay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mMediaController.show();
                }
            });
        }

        void destroyController() {
            mMediaController.hide();
            mMediaController.setAnchorView(null);
            mMediaController = null;
            mDisplay.setOnClickListener(null);
        }

        void setViewBounds(int videoWidth, int videoHeight, View... views) {
            DisplayMetrics dm = getResources().getDisplayMetrics();

            final float wd, hd, wv, hv;
            wd = dm.widthPixels;
            hd = dm.heightPixels;
            wv = videoWidth;
            hv = videoHeight;

            final float rd, rv;
            rd = wd / hd;
            rv = wv / hv;

            final int wf, hf;
            if (rd > rv) {
                wf = (int) (wv * hd / hv + 0.5f);
                hf = (int) hd;
            } else {
                wf = (int) wd;
                hf = (int) (hv * wd / wv + 0.5f);
            }

            for (final View v : views) {
                v.post(new Runnable() {
                    @Override
                    public void run() {
                        ViewGroup.LayoutParams lp = v.getLayoutParams();
                        if (lp == null) {
                            throw new AssertionError("LayoutParams on TextureView should not be null!");
                        }
                        lp.width = wf;
                        lp.height = hf;
                        v.requestLayout();
                    }
                });
            }
        }

        @Override
        public void onPause() {
            if (mMediaController != null) {
                destroyController();
            }

            if (!getActivity().isChangingConfigurations()) {
                destroyMedia();
                mTextureReady = false;
            }
            mResumed = false;
            super.onPause();
        }

        @Override
        public void onDestroyView() {
            mDisplay = null;
            super.onDestroyView();
        }


        MediaController.MediaPlayerControl mControllerBridge = new MediaController.MediaPlayerControl() {
            @Override
            public void start() {
                mMediaPlayer.start();
            }

            @Override
            public void pause() {
                mMediaPlayer.pause();
            }

            @Override
            public int getDuration() {
                return mMediaPlayer.getDuration();
            }

            @Override
            public int getCurrentPosition() {
                return mMediaPlayer.getCurrentPosition();
            }

            @Override
            public void seekTo(int pos) {
                mMediaPlayer.seekTo(pos);
            }

            @Override
            public boolean isPlaying() {
                return mMediaPlayer.isPlaying();
            }

            @Override
            public int getBufferPercentage() {
                return mAmountBuffered;
            }

            @Override
            public boolean canPause() {
                return true;
            }

            @Override
            public boolean canSeekBackward() {
                return true;
            }

            @Override
            public boolean canSeekForward() {
                return true;
            }

            @Override
            public int getAudioSessionId() {
                return mMediaPlayer.getAudioSessionId();
            }
        };
    }
}
