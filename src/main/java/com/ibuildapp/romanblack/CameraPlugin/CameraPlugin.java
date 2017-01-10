/****************************************************************************
 *                                                                           *
 *  Copyright (C) 2014-2015 iBuildApp, Inc. ( http://ibuildapp.com )         *
 *                                                                           *
 *  This file is part of iBuildApp.                                          *
 *                                                                           *
 *  This Source Code Form is subject to the terms of the iBuildApp License.  *
 *  You can obtain one at http://ibuildapp.com/license/                      *
 *                                                                           *
 ****************************************************************************/
package com.ibuildapp.romanblack.CameraPlugin;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;
import com.appbuilder.sdk.android.AppBuilderModuleMain;
import com.appbuilder.sdk.android.StartUpActivity;
import com.appbuilder.sdk.android.Utils;
import com.appbuilder.sdk.android.Widget;
import com.appbuilder.sdk.android.authorization.Authorization;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Main module class. Module entry point.
 * Represents Take a picture widget.
 */
@StartUpActivity(moduleName = "Camera")
public class CameraPlugin extends AppBuilderModuleMain {

    private final String TAG = "com.ibuildapp.CameraPlugin";
    private final int EMAIL_SEND = 1889;
    private final int INITIALIZATION_FAILED = 0;
    private final int NEED_INTERNET_CONNECTION = 1;
    private final int NO_IMAGE = 2;
    private final int CHECK_CONTROLS_STATE = 3;
    private final int SHOW_BUTTON = 4;
    private final int HIDE_BUTTON = 5;
    private final int HAVE_NO_CAMERA = 6;
    private final int LOAD_IMAGE_SHARING = 7;
    private final int CAMERA_HARDWARE_ERROR = 8;
    private final int FACEBOOK_AUTHORIZATION_ACTIVITY = 10000;
    private final int TWITTER_AUTHORIZATION_ACTIVITY = 10001;
    private final int FACEBOOK_PUBLISH_ACTIVITY = 10002;
    private final int TWITTER_PUBLISH_ACTIVITY = 10003;
    private final int TURN_BUTTON_COLOR_BACK = 90;
    private String cachePath = "";
    private ImageView photoButton = null;
    private Button btnShare = null;
    private Bitmap image = null;
    private Widget widget = null;
    private File pictureFile = null;
    private CameraParcer parser = null;
    private Camera camera = null;
    private RelativeLayout previewLayout = null;
    private CameraPreview preview = null;
    private int activeTime = 4;
    private int screenWidth;
    private int screenHeight;
    // Brazhnik update
    private PackageManager pm;
    private View galery_view;
    Drawable d;
    // xml btnSaveImage
    private RelativeLayout share_root_layout;
    private LinearLayout retake_layout;
    private LinearLayout btnShareFacebook;
    private LinearLayout btnShareTwitter;
    private LinearLayout btnShareEmail;
    private ImageView showimage;
    private LinearLayout btnSaveImage;
    private boolean wasCameraClick = false;
    private String image_url;

    private enum State {

        CAPTURING, SHARING
    }

    /**
     * This callback is calling when picture was taken from device camera.
     */
    private PictureCallback mPicture = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            if (data == null) {
                handler.sendEmptyMessage(CAMERA_HARDWARE_ERROR);
                return;
            }
            try {
                image.recycle();
            } catch (NullPointerException nPEx) {
            }

            MediaPlayer mp = MediaPlayer.create(CameraPlugin.this,
                    R.raw.romanblack_camera_shot_sound);
            mp.setVolume(10, 10);
            mp.start();

            // creating file path
            File picturePath = new File(cachePath);
            if (picturePath == null) {
                return;
            }
            if (picturePath.mkdirs()) {
            }

            pictureFile = new File(cachePath + "/image.jpg");
            if (pictureFile == null) {
                return;
            }

            // saving bytes to file 
            try {
                // write image file so sdcard
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                // пытаемся почистить место после того как сохранили фоточку на диск
                System.gc();
                // destroy camera object
                stopCamera();
            } catch (Exception e) {
                Log.w("CameraPlugin", "");
            }

            // set appropriate picture rotation
            correctPictureRotation();

            // send message to handler to get to next step -> loading image
            handler.sendEmptyMessage(LOAD_IMAGE_SHARING);
        }
    };

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case INITIALIZATION_FAILED: {    // activity received wrong data
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_cannot_init, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();

                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            finish();
                        }
                    }, 5000);
                }
                break;
                case NEED_INTERNET_CONNECTION: {    // no internet connection
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_no_internet, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();

                    new Handler().postDelayed(new Runnable() {
                        public void run() {
                            finish();
                        }
                    }, 5000);
                }
                break;
                case NO_IMAGE: {
                    Toast.makeText(CameraPlugin.this, R.string.warning_camera_take_picture_before,
                            Toast.LENGTH_LONG).show();
                }
                break;
                case CHECK_CONTROLS_STATE: {
                    checkControlsState();
                }
                break;
                case HIDE_BUTTON: {
                    hideButton();
                }
                break;
                case SHOW_BUTTON: {
                    showButton();
                }
                break;
                case HAVE_NO_CAMERA: {
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.warning_camera_nocamera, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                    finish();
                }
                break;
                case LOAD_IMAGE_SHARING: {    // state to btnSaveImage picture
                    loadImageSharing_v2();
                    wasCameraClick = false;
                }
                break;
                case CAMERA_HARDWARE_ERROR: {
                    Toast.makeText(CameraPlugin.this,
                            "Camera error! Please try again.",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;

                case TURN_BUTTON_COLOR_BACK: {    // release button and turn color back
                    d.clearColorFilter();
                    galery_view.setBackgroundDrawable(d);
                }
                break;
            }
        }
    };

    @Override
    public void create() {
        try {
            Display display = getWindowManager().getDefaultDisplay();
            screenWidth = display.getWidth();
            screenHeight = display.getHeight();

            // set our custom layout
            setContentView(R.layout.romanblack_camera_preview);

            // obtaining widget data
            Intent currentIntent = getIntent();
            Bundle store = currentIntent.getExtras();
            widget = (Widget) store.getSerializable("Widget");
            if (widget == null) {
                handler.sendEmptyMessageDelayed(INITIALIZATION_FAILED, 100);
                return;
            }

            if (widget.getTitle() != null && widget.getTitle().length() != 0) {
                setTopBarTitle(widget.getTitle());
            } else {
                setTopBarTitle(getResources().getString(R.string.camera_plugin));
            }

            setTopBarLeftButtonText(getResources().getString(R.string.common_home_upper), true, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    finish();
                }
            });

            String xml = widget.getPluginXmlData().length() == 0
                    ? Utils.readXmlFromFile(widget.getPathToXmlFile())
                    : widget.getPluginXmlData();

            parser = new CameraParcer(xml);
            parser.parse();

            // concatenating cache path
            cachePath = widget.getCachePath() + "/camera-" + widget.getOrder();

            /**
             * Changing panel background color
             *
             */
            LinearLayout panel = (LinearLayout) findViewById(R.id.romanblack_camera_panel);
            panel.setOnClickListener(new OnClickListener() {
                public void onClick(View view) {
                    if (!wasCameraClick) {
                        wasCameraClick = true;

                        Camera.Parameters parameters = camera.getParameters();
                    /*    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        parameters.setPictureFormat(ImageFormat.JPEG);
                        parameters.setAntibanding(Camera.Parameters.ANTIBANDING_AUTO);
                        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
*/
                         List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
                        Camera.Size maxSize = sizes.get(0);
                       for (Camera.Size size : sizes) {
                            if (maxSize.height < size.height) {
                                maxSize = size;
                            }
                        }
                        Log.d(TAG, String.format("height = %d, width = %d", maxSize.height, maxSize.width));

                        parameters.setPreviewSize(maxSize.width, maxSize.height);
                        //parameters.setPictureSize(maxSize.width, maxSize.height);
                        camera.setParameters(parameters);
                        camera.takePicture(null, null, mPicture);
                        // play sound
                        MediaPlayer mp = MediaPlayer.create(CameraPlugin.this,
                                R.raw.romanblack_camera_shot_sound);
                        mp.setVolume(10, 10);
                        mp.start();
                    }
                }
            });


            /**
             * Starting camera
             *
             */
            pm = this.getPackageManager();
            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                startCamera(10);
            } else {
                handler.sendEmptyMessageDelayed(HAVE_NO_CAMERA, 100);
            }

        } catch (Exception e) {
        }
    }

    /**
     * Calling when child activity finished it's work and trying to return
     * result
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case TWITTER_AUTHORIZATION_ACTIVITY: {
                if (resultCode == RESULT_OK) {
                    shareTwitter();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_twitter_posted_error, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                }

            }
            break;

            case FACEBOOK_AUTHORIZATION_ACTIVITY: {
                if (resultCode == RESULT_OK) {
                    shareFacebook();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_facebook_posted_error, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                }
            }
            break;

            case TWITTER_PUBLISH_ACTIVITY: {
                if (resultCode == RESULT_OK) {
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_twitter_posted_success, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_twitter_posted_error, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                }
            }
            break;

            case FACEBOOK_PUBLISH_ACTIVITY: {
                if (resultCode == RESULT_OK) {
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_facebook_posted_success, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast toast = Toast.makeText(CameraPlugin.this, R.string.alert_facebook_posted_error, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                }
            }
            break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {

        if (camera != null) {
            camera.stopPreview();
            appropriateOrientation();
        }
        super.onConfigurationChanged(newConfig);

    }

    @Override
    public void destroy() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.release();
            } catch (Exception ex) {
            }
        }
        super.destroy();
    }

    /**
     * Hides share button.
     */
    private void hideButton() {
        btnShare.setVisibility(View.INVISIBLE);
    }

    /**
     * Shows share button.
     */
    private void showButton() {
        btnShare.setVisibility(View.VISIBLE);

        activeTime = 4;
        checkControlsState();
    }

    /**
     * Checks if need to hide share button.
     */
    private void checkControlsState() {
        if (activeTime > 0) {
            activeTime--;
            handler.sendEmptyMessageDelayed(CHECK_CONTROLS_STATE, 1000);
        } else {
            handler.sendEmptyMessageDelayed(HIDE_BUTTON, 1000);
        }
    }

    /**
     * Stops the camera and destroys dependent objects.
     */
    private void stopCamera() {
        System.out.println("stopCamera method");
        if (camera != null) {
            // remove camera preview
            camera.stopPreview();
            camera.setPreviewCallback(null);
            previewLayout.removeView(preview);

            // destroy camera object
            camera.release();
            camera = null;
        }
    }

    /**
     * Starts the camera and creates SurfaceView for camera preview.
     */
    private void startCamera(int camIdx) {
        camera = Camera.open(); // using IDX only in API level 9

        Camera.Parameters cPrm = camera.getParameters();
      /*  cPrm.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
        cPrm.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);*/

        List<Camera.Size> sizesList = cPrm.getSupportedPreviewSizes();
        Camera.Size maxSize1 = sizesList.get(0);
        for (Camera.Size size : sizesList) {
            if (maxSize1.height < size.height) {
                maxSize1 = size;
            }
        }

        cPrm.setPreviewSize(maxSize1.width, maxSize1.height);
        camera.setParameters(cPrm);
        preview = new CameraPreview(getApplicationContext(), camera);
        Camera.Parameters camParam = camera.getParameters();
        Camera.Size size = camParam.getPreviewSize();

        float ratio = size.height / size.width;
        int newH = (int) ((int) screenWidth / ratio);
        int newW = screenWidth;
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(newW, newH);
        previewLayout = (RelativeLayout) findViewById(R.id.romanblack_camera_preview);
        previewLayout.addView(preview, params);

        appropriateOrientation();
    }

    /**
     * Checks the device model and rotates the preview depending on it.
     */
    private void appropriateOrientation() {
        String myDeviceModel = android.os.Build.MODEL;

        // orientation of screen depends on device type!
        if (myDeviceModel.compareTo("GT-I9100") == 0) {
            changeLandscapeToPortrait();
        } else if (myDeviceModel.compareTo("LG-P500") == 0) {
            changeLandscapeToPortrait();
        } else {
            changeLandscapeToPortrait();
        }
    }

    /**
     * Sets jpeg image quality.
     * @param quality - value between 1-100
     */
    private void setCameraQuality(int quality) {
        Camera.Parameters cameraParams = camera.getParameters();
        cameraParams.setJpegQuality(quality);
        camera.setParameters(cameraParams);
    }

    /**
     * Changes camera preview orientation initial camera rotation - landscape.
     */
    public void changeLandscapeToPortrait() {
        int surfRotation = ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();
        int degrees = 0;
        int rotation = 0;
        if (surfRotation == Surface.ROTATION_0) {
            rotation = 90;
            degrees = 90;
        } else if (surfRotation == Surface.ROTATION_90) {
            rotation = 0;
            degrees = 0;
        } else if (surfRotation == Surface.ROTATION_180) {
            rotation = 270;
            degrees = 270;
        } else if (surfRotation == Surface.ROTATION_270) {
            rotation = 180;
            degrees = 180;
        }

        camera.setDisplayOrientation(degrees);
        Camera.Parameters cameraParams = camera.getParameters();
        cameraParams.setRotation(rotation);
        camera.setParameters(cameraParams);
    }

    /**
     * Changes camera preview orientation initial camera rotation - portrait.
     */
    public void changePortraitToLandscape() {
        int surfRotation = ((WindowManager) getSystemService(WINDOW_SERVICE))
                .getDefaultDisplay().getRotation();
        int degrees = 0;
        int rotation = 0;
        if (surfRotation == Surface.ROTATION_0) {
            rotation = 0;
            degrees = 0;
        } else if (surfRotation == Surface.ROTATION_90) {
            rotation = 90;
            degrees = 90;
        } else if (surfRotation == Surface.ROTATION_180) {
            rotation = 180;
            degrees = 180;
        } else if (surfRotation == Surface.ROTATION_270) {
            rotation = 270;
            degrees = 270;
        }

        camera.setDisplayOrientation(degrees);
        Camera.Parameters cameraParams = camera.getParameters();
        cameraParams.setRotation(rotation);
        camera.setParameters(cameraParams);
    }

    /**
     * Corrects the picture rotation.
     */
    private void correctPictureRotation() {
        // get picture width and height
        System.gc();

        // getting image size and save
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pictureFile.getAbsolutePath(), opts);
        int width = opts.outWidth;
        int height = opts.outHeight;
        System.gc();

        Matrix matrix = new Matrix();
        opts = new BitmapFactory.Options();

        // if picture resolution lower then 800*600 
        // do not resize it
        if (width < 600 || height < 600) {
            opts = null;
        } else {
            opts.inSampleSize = 2;
        }

        // decode image with appropriate options 
        try {
            System.gc();
            image = BitmapFactory.decodeFile(pictureFile.getAbsolutePath(), opts);
        } catch (Exception ex) {
            Log.d("", "");
        } catch (OutOfMemoryError e) {
            Log.d("", "");
            System.gc();
            try {
                image = BitmapFactory.decodeFile(pictureFile.getAbsolutePath());
            } catch (Exception ex) {
                Log.d("", "");
            } catch (OutOfMemoryError ex) {
                Log.d("", "");
                finish();
            }
        }

        try {
            ExifInterface exifInterface = new ExifInterface(pictureFile.getAbsolutePath());

            int rotation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (rotation) {
                case ExifInterface.ORIENTATION_NORMAL: {
                }
                break;
                case ExifInterface.ORIENTATION_ROTATE_90: {
                    matrix.postRotate(90);
                    image = Bitmap.createBitmap(image, 0, 0,
                            image.getWidth(), image.getHeight(), matrix,
                            true);
                }
                break;
                case ExifInterface.ORIENTATION_ROTATE_180: {
                    matrix.postRotate(180);
                    image = Bitmap.createBitmap(image, 0, 0,
                            image.getWidth(), image.getHeight(), matrix,
                            true);
                }
                break;
                case ExifInterface.ORIENTATION_ROTATE_270: {
                    matrix.postRotate(270);
                    image = Bitmap.createBitmap(image, 0, 0,
                            image.getWidth(), image.getHeight(), matrix,
                            true);
                }
                break;
            }

            // convert bitmap to jpeg with 50% compression
            image.compress(Bitmap.CompressFormat.JPEG, 100,
                    new FileOutputStream(pictureFile));

        } catch (Exception e) {
            Log.d("", "");
        }
    }

    /**
     * Shows layout that contains sharing buttons.
     */
    private void loadImageSharing_v2() {
        boolean available_internet = true;

        // Change layout to btnSaveImage photo
        setContentView(R.layout.sergeybrazhnik_camera_main);

        // checking Internet connection
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        if (!((ni != null) && (ni.isConnectedOrConnecting()))) {
            available_internet = false;
        }

        // getting root layout
        share_root_layout = (RelativeLayout) findViewById(R.id.share_root_layout);
        showimage = (ImageView) findViewById(R.id.imageView);

        // computing screen width and density
        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        float density = getResources().getDisplayMetrics().density;

        // creating root layout for footer
        LinearLayout footer = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.sergeybrazhnik_footer, null);
        Drawable dd = footer.getBackground();
        dd.setAlpha(127);
        footer.setBackgroundDrawable(dd);

        btnSaveImage = (LinearLayout) footer.findViewById(R.id.camera_save_btn);
        btnShareFacebook = (LinearLayout) footer.findViewById(R.id.camera_facebook_btn);
        btnShareTwitter = (LinearLayout) footer.findViewById(R.id.camera_twitter_btn);
        btnShareEmail = (LinearLayout) footer.findViewById(R.id.camera_email_btn);

        RelativeLayout.LayoutParams footer_params =
                new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        // adding align rules
        footer_params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        footer_params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        // creating retake layout-button
        retake_layout = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.romanblack_retake, null);
        RelativeLayout.LayoutParams retake_param =
                new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        retake_param.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        retake_param.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        retake_param.setMargins(0, (int) (10 * density), (int) (10 * density), 0);

        share_root_layout.addView(footer, footer_params);
        share_root_layout.addView(retake_layout, retake_param);

        if (image != null) {
            showimage.setImageBitmap(image);
        }

        // save picture to Galery
        btnSaveImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // set color filter to button
                d = view.getBackground();
                d.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
                galery_view = view;
                view.setBackgroundDrawable(d);

                // directly save picture to galery
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                image_url = MediaStore.Images.Media.insertImage(getContentResolver(), image, "IMG_" + timeStamp, "description");

                if (image_url != null) {
                    Toast toast = Toast.makeText(CameraPlugin.this, getResources().getString(R.string.alert_galary_save_success), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                } else {
                    Toast toast = Toast.makeText(CameraPlugin.this, getResources().getString(R.string.alert_galary_save_error), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                }

                // unset color filter from button
                handler.sendEmptyMessageDelayed(TURN_BUTTON_COLOR_BACK, 400);
            }
        });

        // retake button handler
        retake_layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setContentView(R.layout.romanblack_camera_preview);
                photoButton = (ImageView) findViewById(R.id.romanblack_btn_camera_capture);

                // handler "take a pic" button
                photoButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (!wasCameraClick) {
                            camera.autoFocus(new Camera.AutoFocusCallback() {
                                public void onAutoFocus(boolean arg0, Camera arg1) {
                                    wasCameraClick = true;
                                    camera.takePicture(null, null, mPicture);
                                }
                            });

                        }
                    }
                });

                stopCamera();
                startCamera(10);
            }
        });

        // save picture on btnShareFacebook
        btnShareFacebook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // checking Internet connection
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (!((ni != null) && (ni.isConnectedOrConnecting()))) {
                    redrawButtons(false);
                    Toast toast = Toast.makeText(CameraPlugin.this, getResources().getString(R.string.alert_no_internet), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                } else {
                    redrawButtons(true);
                    Authorization.authorize(CameraPlugin.this, FACEBOOK_AUTHORIZATION_ACTIVITY, Authorization.AUTHORIZATION_TYPE_FACEBOOK);
                }
            }
        });

        // save picture on btnShareTwitter
        btnShareTwitter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // checking Internet connection
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (!((ni != null) && (ni.isConnectedOrConnecting()))) {
                    redrawButtons(false);
                    Toast toast = Toast.makeText(CameraPlugin.this, getResources().getString(R.string.alert_no_internet), Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                } else {
                    redrawButtons(true);
                    Authorization.authorize(CameraPlugin.this, TWITTER_AUTHORIZATION_ACTIVITY, Authorization.AUTHORIZATION_TYPE_TWITTER);
                }
            }
        });

        // btnSaveImage picture on btnShareEmail
        btnShareEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // checking Internet connection
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni = cm.getActiveNetworkInfo();
                if (!((ni != null) && (ni.isConnectedOrConnecting()))) {
                    redrawButtons(false);
                    Toast toast = Toast.makeText(CameraPlugin.this, "No Internet connection available.", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 95);
                    toast.show();
                } else {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/html");
                    String mail = parser.getEmail();
                    intent.putExtra(Intent.EXTRA_EMAIL, new String[]{mail});
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(pictureFile));
                    startActivityForResult(intent, EMAIL_SEND);
                }
            }
        });
    }

    /**
     * Redraws the sharing buttons depending on network connection.
     * @param internet true if the device has network connection
     */
    private void redrawButtons(boolean internet) {
        Drawable myIcon = null;
        ColorMatrix matrix = null;
        ColorMatrixColorFilter filter = null;
        if (internet) {
            // btnShareTwitter button
            btnShareTwitter.setEnabled(true);
//            myIcon = getResources().getDrawable(R.drawable.romanblack_camera_twitter);
            myIcon = getResources().getDrawable(R.drawable.twitter_logo);
            myIcon.clearColorFilter();
            btnShareTwitter.setBackgroundDrawable(myIcon);

            // btnShareFacebook button
            btnShareFacebook.setEnabled(true);
//            myIcon = getResources().getDrawable(R.drawable.romanblack_camera_facebook);
            myIcon = getResources().getDrawable(R.drawable.facebook_logo);
            myIcon.clearColorFilter();
            btnShareFacebook.setBackgroundDrawable(myIcon);

            // btnShareEmail button
            btnShareEmail.setEnabled(true);
//            myIcon = getResources().getDrawable(R.drawable.romanblack_camera_email);
            myIcon = getResources().getDrawable(R.drawable.dnevolin_email_button_background);
            myIcon.clearColorFilter();
            btnShareEmail.setBackgroundDrawable(myIcon);
        } else {
            // btnShareTwitter button
//            myIcon = getResources().getDrawable(R.drawable.romanblack_camera_twitter);
            myIcon = getResources().getDrawable(R.drawable.twitter_logo);
            matrix = new ColorMatrix();
            matrix.setSaturation(0);
            filter = new ColorMatrixColorFilter(matrix);
            myIcon.setColorFilter(filter);
            btnShareTwitter.setBackgroundDrawable(myIcon);
            btnShareTwitter.setEnabled(false);

            // btnShareFacebook button
//            myIcon = getResources().getDrawable(R.drawable.romanblack_camera_facebook);
            myIcon = getResources().getDrawable(R.drawable.facebook_logo);
            matrix = new ColorMatrix();
            matrix.setSaturation(0);
            filter = new ColorMatrixColorFilter(matrix);
            myIcon.setColorFilter(filter);
            btnShareFacebook.setBackgroundDrawable(myIcon);
            btnShareFacebook.setEnabled(false);

            // btnShareEmail button
//            myIcon = getResources().getDrawable(R.drawable.romanblack_camera_email);
            myIcon = getResources().getDrawable(R.drawable.dnevolin_email_button_background);
            matrix = new ColorMatrix();
            matrix.setSaturation(0);
            filter = new ColorMatrixColorFilter(matrix);
            myIcon.setColorFilter(filter);
            btnShareEmail.setBackgroundDrawable(myIcon);
            btnShareEmail.setEnabled(false);
        }
    }

    /**
     * This class using for camera previewing.
     */
    class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

        private SurfaceHolder mHolder;
        private Camera mCamera;

        public CameraPreview(Context context, Camera camera) {
            super(context);

            mCamera = camera;
            mHolder = getHolder();
            mHolder.addCallback(this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            Log.d("", "");
        }

        public void surfaceCreated(SurfaceHolder holder) {
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (Exception e) {
                Log.w("CameraPlugin", e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            if (mHolder.getSurface() == null) {
                return;
            }

            try {
                mCamera.stopPreview();
            } catch (Exception e) {
            }

            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();

            } catch (Exception e) {
                Log.w("CameraPreview", e.getMessage());
            }
        }
    }

    /**
     * Starts SharingActivity to share on Twitter.
     */
    private void shareTwitter() {
        Intent it = new Intent(CameraPlugin.this, SharingActivity.class);
        image_url = saveImage();
        // pass the picture path and start the activity
        it.putExtra("image", image_url);
        it.putExtra("hasAd", widget.isHaveAdvertisement());
        it.putExtra("appname", widget.getAppName());
        it.putExtra("type", "twitter");
        startActivityForResult(it, TWITTER_PUBLISH_ACTIVITY);
    }

    /**
     * Starts SharingActivity to share on Facebook.
     */
    private void shareFacebook() {
        Intent it = new Intent(CameraPlugin.this, SharingActivity.class);
        image_url = saveImage();

        it.putExtra("image", image_url);
        it.putExtra("hasAd", widget.isHaveAdvertisement());
        it.putExtra("appname", widget.getAppName());
        it.putExtra("type", "facebook");
        startActivityForResult(it, FACEBOOK_PUBLISH_ACTIVITY);
    }

    /**
     * Saves the captured image to device external storage.
     * @return the saved file path string
     */
    private String saveImage() {
        String filename = "";

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            cachePath = widget.getCachePath();
            filename = cachePath + "/" + timeStamp + ".jpeg";
            File file = new File(filename);
            FileOutputStream out = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return filename;
    }
}
