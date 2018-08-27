package com.itb.tf.opencv;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoadImage extends AppCompatActivity {

    ImageView imgContainer;
    boolean drawRect = false;
    boolean invert = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_image);

        OpenCVLoader.initDebug();

        imgContainer = findViewById(R.id.imgContainer);

    }

    public void openGalery(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, 101);
    }

    protected void onActivityResult(int RequestCode,int ResultCode, Intent Data) {
        super.onActivityResult(RequestCode, ResultCode, Data);

        if(RequestCode == 101 && ResultCode == RESULT_OK && Data != null) {
            Uri imageUri = Data.getData();

            String path = getPath(imageUri);
            loadImage(path);
        }
    }

    private void loadImage(String path) {
        //create bitmap
        Bitmap bitmap=null;
        File f= new File(path);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        try {
            bitmap = BitmapFactory.decodeStream(new FileInputStream(f), null, options);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        //convert bitmap to mat opencv
        Mat bgr = new Mat(bitmap.getHeight(),bitmap.getWidth(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap,bgr);
        Imgproc.cvtColor(bgr, bgr, Imgproc.COLOR_BGRA2BGR);

        //run grabcut algorithm
        // bgr = grabcut(bgr);

        //run kmeans with cie convert
        Mat mask = cluster(cieConvert(bgr),5).get(0);

        //convert to gray for create mask
        Imgproc.cvtColor(mask, mask, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(mask, mask, 128, 255, Imgproc.THRESH_OTSU);

        //invert color
        if(invert) {
            Core.bitwise_not(mask,mask);
        }

        mask.convertTo(mask, CvType.CV_8U);

        //mask bgr
        Mat masked = new Mat();
        bgr.copyTo(masked,mask);

        //display to imageview with bitmap
        Utils.matToBitmap(masked,bitmap);
        imgContainer.setImageBitmap(bitmap);
    }

    private Mat cieConvert(Mat mat) {
        Mat tmp = mat.clone();
        Mat cie = mat.clone();

        //gaussian filter
        Size size = new Size(5,5);
        Imgproc.GaussianBlur(mat,tmp,size,2);

        //to lab
        Imgproc.cvtColor(tmp,cie,Imgproc.COLOR_BGR2Lab);

        return cie;
    }

    private String getPath(Uri uri) {
        if (uri == null) {
            return null;
        } else {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(uri,projection,null,null,null);

            if(cursor != null) {
                int colIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                cursor.moveToFirst();

                return cursor.getString(colIdx);
            }
        }
        return uri.getPath();
    }


    public static List<Mat> cluster(Mat cutout, int k) {
        Mat samples = cutout.reshape(1, cutout.cols() * cutout.rows());
        Mat samples32f = new Mat();
        samples.convertTo(samples32f, CvType.CV_32F, 1.0 / 255.0);

        Mat labels = new Mat();
        TermCriteria criteria = new TermCriteria(TermCriteria.COUNT, 100, 1);
        Mat centers = new Mat();
        Core.kmeans(samples32f, k, labels, criteria, 1, Core.KMEANS_PP_CENTERS, centers);
        return showClusters(cutout, labels, centers);
    }

    private static List<Mat> showClusters (Mat cutout, Mat labels, Mat centers) {
        centers.convertTo(centers, CvType.CV_8UC1, 255.0);
        centers.reshape(3);

        List<Mat> clusters = new ArrayList<Mat>();
        for(int i = 0; i < centers.rows(); i++) {
            clusters.add(Mat.zeros(cutout.size(), cutout.type()));
        }

        Map<Integer, Integer> counts = new HashMap<Integer, Integer>();
        for(int i = 0; i < centers.rows(); i++) counts.put(i, 0);

        int rows = 0;
        for(int y = 0; y < cutout.rows(); y++) {
            for(int x = 0; x < cutout.cols(); x++) {
                int label = (int)labels.get(rows, 0)[0];
                int r = (int)centers.get(label, 2)[0];
                int g = (int)centers.get(label, 1)[0];
                int b = (int)centers.get(label, 0)[0];
                counts.put(label, counts.get(label) + 1);
                clusters.get(label).put(y, x, b, g, r);
                rows++;
            }
        }
        System.out.println(counts);
        return clusters;
    }

    private Mat grabcut(Mat mat) {

        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2RGB, 0);
        Mat mask = new Mat();
        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();
        Rect rect = new Rect(mat.cols() * 10/100, mat.rows() * 10/100, mat.cols() * 80/100, mat.rows() * 80/100);
        Imgproc.grabCut(mat, mask, rect, bgdModel, fgdModel, 1, Imgproc.GC_INIT_WITH_RECT);

        // draw foreground
        for (int i = 0; i < mat.rows(); i++) {
            for (int j = 0; j < mat.cols(); j++) {
                if (mask.get(i, j)[0] == 0 || mask.get(i, j)[0] == 2) {
                    double[] data = mat.get(i, j);

                    data[0] = 0;
                    data[1] = 0;
                    data[2] = 0;

                    mat.put(i, j,data);
                }
            }
        }

        // draw grab rect
        if (drawRect) {
            Scalar color = new Scalar(0, 0, 255);
            Point point1 = new Point(rect.x, rect.y);
            Point point2 = new Point(rect.x + rect.width, rect.y + rect.height);
            Imgproc.rectangle(mat, point1, point2, color);
        }

        return mat;
    }

}
