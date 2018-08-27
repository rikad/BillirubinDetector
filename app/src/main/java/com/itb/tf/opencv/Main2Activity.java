package com.itb.tf.opencv;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCamera2View;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class Main2Activity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {


    // Used for logging success or failure messages
    private static final String TAG = "OCVSample::Activity";

    // Loads camera view of OpenCV for us to use. This lets us see using OpenCV
    private CameraBridgeViewBase mOpenCvCameraView;

    // Used in Camera selection from menu (when implemented)
    private boolean  mIsJavaCamera = true;
    private MenuItem mItemSwitchCamera = null;

    // These variables are used (at the moment) to fix camera orientation from 270degree to 0degree
    Mat mRgba,mRgbaF,mRgbaT,mCieLab,mResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);


        Log.i(TAG, "called onCreate");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mOpenCvCameraView = (JavaCameraView) findViewById(R.id.CameraSurface);

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);

        mOpenCvCameraView.setCvCameraViewListener(this);


    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

   public Main2Activity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {

        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
        mCieLab = new Mat(width, width, CvType.CV_8UC4);
        mResult = new Mat(width, width, CvType.CV_8UC4);

    }

    public void onCameraViewStopped() {
        mRgba.release();
    }


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        // TODO Auto-generated method stub
        mRgba = inputFrame.rgba();

        // Rotate mRgba 90 degrees
        Core.transpose(mRgba, mRgbaT);
        Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0,0, 0);
        Core.flip(mRgbaF, mRgba, 1 );

        mResult = grabcut(mRgba);

        return mRgba; // This function must return
    }

    private Mat grabcut(Mat mat) {

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB, 0);
        Mat mask = new Mat();
        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        Rect rect = new Rect(50, 50, 260, 280);
        Imgproc.grabCut(mat, mask, rect, bgdModel, fgdModel, 1, Imgproc.GC_INIT_WITH_RECT);

        // draw foreground
        for (int i = 0; i < mat.rows(); i++) {
            for (int j = 0; j < mat.cols(); j++) {
                if (mask.get(i, j)[0] == 0 || mask.get(i, j)[0] == 2) {
                    mat.get(i, j)[0] = 0;
                    mat.get(i, j)[1] = 0;
                    mat.get(i, j)[2] = 0;
                }
            }
        }

        // draw grab rect
        Scalar color = new Scalar(0, 0, 255);
        Point point1 = new Point(rect.x, rect.y);
        Point point2 = new Point(rect.x + rect.width, rect.y + rect.height);
        Imgproc.rectangle(mat, point1, point2, color);

        return mat;
    }

}
