package jerome.com.usbcamera;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

/**
 * Created by yanbo on 2018/3/22.
 */

public class SimpleMainActivity extends Activity {
    final private String TAG = this.getClass().toString();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.setContentView(R.layout.simple_main);
    }
}
