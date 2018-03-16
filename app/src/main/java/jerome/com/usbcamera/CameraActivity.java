package jerome.com.usbcamera;

import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener, CameraView.OnPictureSaved {

    private ImageButton mCaptureButton;
    private CameraView mUsbCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.main);

        mCaptureButton = (ImageButton) findViewById(R.id.capture_btn);
        mCaptureButton.setOnClickListener(this);

        mUsbCamera = (CameraView) findViewById(R.id.camera_view);
        mUsbCamera.setSavedCallback(this);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {

            case R.id.capture_btn:
                mUsbCamera.capturePicture();
                break;
        }
    }

    @Override
    public void onPictureSaved(Bitmap bmp) {
        Intent it = new Intent(CameraActivity.this, RegisterActivity.class);
        Bundle bundle = new Bundle();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        bundle.putByteArray("image", baos.toByteArray());
        it.putExtras(bundle);
        startActivity(it);
    }
}
