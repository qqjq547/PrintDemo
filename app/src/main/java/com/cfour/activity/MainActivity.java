package com.cfour.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.cfour.print.R;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import woyou.aidlservice.jiuiv5.ICallback;
import woyou.aidlservice.jiuiv5.IWoyouService;

public class MainActivity extends Activity {

	TextView tvStart,tvPause;
//	String baseUrl = "http://upload.depmemo.com/c4.aspx?type=";
//    String baseUrl = "http://print.depmemo.com/print.aspx?id=";
	OkHttpClient client;
    Thread thread;
	SharedPreferences sp;
	String host;
	LinearLayout linLink,linStart;
	EditText etHost;
	TextView tvSave;
	TextView tvSetting;
	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		linLink=findViewById(R.id.lin_link);
		etHost=findViewById(R.id.et_host);
		tvSave=findViewById(R.id.tv_save);
		tvPause=findViewById(R.id.tv_pause);
		tvStart=findViewById(R.id.tv_start);
		linStart=findViewById(R.id.lin_start);
		tvSetting=findViewById(R.id.tv_setting);
		sp = getSharedPreferences("print", Context.MODE_PRIVATE);
		host=sp .getString("host","");
		if (TextUtils.isEmpty(host)){
			linLink.setVisibility(View.VISIBLE);
			tvPause.setVisibility(View.GONE);
		}else {
			linLink.setVisibility(View.GONE);
			tvPause.setVisibility(View.VISIBLE);
			startConnect();
		}
		tvSave.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				 host=etHost.getText().toString().trim();
				if (TextUtils.isEmpty(host)){
					Toast.makeText(MainActivity.this,"链接不能为空",Toast.LENGTH_SHORT).show();
				}else {
					SharedPreferences.Editor editor = sp.edit();
					editor.putString("host", host);
					editor.commit();
					linLink.setVisibility(View.GONE);
					tvPause.setVisibility(View.VISIBLE);
					startConnect();
				}
			}
		});

        tvPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvPause.setVisibility(View.GONE);
				linStart.setVisibility(View.VISIBLE);
                handler.removeMessages(0);
                handler.removeMessages(1);
                handler.removeCallbacks(runnable);
                if (thread!=null&&!thread.isInterrupted()){
                    thread.interrupt();
                }
                thread=null;
            }
        });
        tvStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvPause.setVisibility(View.VISIBLE);
				linStart.setVisibility(View.GONE);
                handler.post(runnable);
            }
        });
		tvSetting.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				tvPause.setVisibility(View.GONE);
				linStart.setVisibility(View.GONE);
				linLink.setVisibility(View.VISIBLE);
			}
		});
	}
    public void startConnect(){
		Intent intent = new Intent();
		intent.setPackage("woyou.aidlservice.jiuiv5");
		intent.setAction("woyou.aidlservice.jiuiv5.IWoyouService");
		startService(intent);//启动打印服务
		bindService(intent, connService, Context.BIND_AUTO_CREATE);
		client = new OkHttpClient();
		tvPause.setVisibility(View.VISIBLE);
		linStart.setVisibility(View.GONE);
		handler.post(runnable);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		handler.removeMessages(0);
		handler.removeMessages(1);
		handler.removeCallbacks(runnable);
		if (thread!=null&&!thread.isInterrupted()){
            thread.interrupt();
        }
        thread=null;
	}

	Runnable runnable = new Runnable() {
		@Override
		public void run() {
			getUrlData();
		}
	};
	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			String result = (String) msg.obj;
			if (msg.what == 1) {//正常结果
				Log.d("hjq","handleMessage="+result);
				if (!TextUtils.isEmpty(result) && result.startsWith("http")) {
					getImageData(result);
				} else {
					postDelayed(runnable, 2000);
				}
				tvPause.setVisibility(View.VISIBLE);
				linStart.setVisibility(View.GONE);
			} else if (msg.what == 0) {//异常错误
				Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
                tvPause.setVisibility(View.GONE);
				linStart.setVisibility(View.VISIBLE);
			}
		}
	};

	public void getUrlData() {
        thread=new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Request request = new Request.Builder()
							.url(host)
							.build();
					Response response = client.newCall(request).execute();
					String resutl=response.body().string();
					Log.d("hjq",resutl);
					if (response.isSuccessful()) {
						sendRsult(resutl.trim());
					} else {
						sendError(resutl.trim());
					}
				} catch (IOException e) {
					e.printStackTrace();
					sendError(e.getMessage());
				}
			}
		});
        thread.start();
	}

	private void getImageData(final String url) {
		Log.d("hjq","getImageData="+url);
        thread=new Thread(new Runnable() {
			@Override
			public void run() {
				final Request request = new Request.Builder().get()
						.url(url)
						.build();
				try {
					Response response = client.newCall(request).execute();
					if (response.isSuccessful()) {
						byte[] bytes = response.body().bytes();
						Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
						printImage(bitmap);
					} else {
						sendError(response.body().toString());
					}
				} catch (IOException e) {
					e.printStackTrace();
					sendError(e.getMessage());
				}
			}
		});
        thread.start();

	}

	public void sendRsult(String msg) {
		Message message = handler.obtainMessage();
		message.what = 1;
		message.obj = msg;
		handler.sendMessage(message);
	}

	public void sendError(String msg) {
		Message message = handler.obtainMessage();
		message.what = 0;
		message.obj = msg;
		handler.sendMessage(message);
	}

	private IWoyouService woyouService;

	private ServiceConnection connService = new ServiceConnection() {

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d("hjq", "onServiceDisconnected=" + name);
			Toast.makeText(MainActivity.this, "服务连接失败", Toast.LENGTH_SHORT).show();
			woyouService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d("hjq", "onServiceConnected=" + name);
			Toast.makeText(MainActivity.this, "服务连接成功", Toast.LENGTH_SHORT).show();
			woyouService = IWoyouService.Stub.asInterface(service);
		}

		@Override
		public void onNullBinding(ComponentName name) {
			Log.d("hjq", "onNullBinding=" + name);
		}

		@Override
		public void onBindingDied(ComponentName name) {
			Log.d("hjq", "onBindingDied=" + name);
		}
	};

	ICallback callback = new ICallback.Stub() {

		@Override
		public void onRunResult(boolean success) throws RemoteException {
			Log.d("hjq", "onRunResult=" + success);
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(MainActivity.this,"打印完成",Toast.LENGTH_SHORT).show();
					sendRsult("");
				}
			});
		}

		@Override
		public void onReturnString(final String value) throws RemoteException {
			Log.d("hjq", "onReturnString=" + value);
            sendError(value);
		}

		@Override
		public void onRaiseException(int code, final String msg)
				throws RemoteException {
			Log.d("hjq", "onRaiseException=" + code + ":" + msg);
            sendError(msg);
		}
	};

	public void printImage(Bitmap mBitmap) {
		if (woyouService==null){
			sendError("woyou 服务无法启动");
			return;
		}
		if (mBitmap==null){
			sendError("图片为空");
			return;
		}
//／＊＊＊＊＊压缩图片＊＊＊＊＊＊＊＊＊＊／
		double gh = (double) mBitmap.getWidth() / 384;
//		if( mBitmap1 == null ){
		mBitmap = zoomBitmap(mBitmap, 384, (int) (mBitmap.getHeight() / gh));
//		}
//／＊＊＊＊＊＊压缩完成＊＊＊＊＊＊＊＊＊／
		try {
			woyouService.setAlignment(1, callback);
			woyouService.printBitmap(mBitmap, callback);
// woyouService.printBitmap(mBitmap2, callback);
			woyouService.lineWrap(3, null);
		} catch (RemoteException e) {
// TODO Auto-generated catch block
			e.printStackTrace();//当图片过大，也没有压缩，打印服务会反馈异常
		}

	}

	public static Bitmap zoomBitmap(Bitmap bitmap, int width, int height) {
		int w = bitmap.getWidth();
		int h = bitmap.getHeight();
		Matrix matrix = new Matrix();
		float scaleWidth = ((float) width / w);
		float scaleHeight = ((float) height / h);
		matrix.postScale(scaleWidth, scaleHeight);
		Bitmap newbmp = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
		return newbmp;
	}

}
