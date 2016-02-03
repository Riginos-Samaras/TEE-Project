package com.cei522.trustedphotocapture.myapplication;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.zip.Adler32;

public class MainActivity extends Activity {
    private static final int CAMERA_REQUEST = 1888;
    private ImageView imageView;
    private ImageView imageView2;
    private boolean mIsBound = false;
    private DaemonService mBoundService;
    boolean mBound = false;
    byte[] signatureBytes = new byte[0];
    String photo_data;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            DaemonService.LocalBinder binder = (DaemonService.LocalBinder) service;
            mBoundService = binder.getService();
            mBound = true;

            // Tell the user about this for our demo.
            Toast.makeText(MainActivity.this, R.string.daemon_service_connected,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBoundService = null;
            mBound = false;
            Toast.makeText(MainActivity.this, R.string.daemon_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.imageView = (ImageView)this.findViewById(R.id.imageView1);
        Button photoButton = (Button) this.findViewById(R.id.button1);
        startService(new Intent(this, DaemonService.class));
        Log.i("MAIN Service", "Starting ReCRED Daemon");
        doBindService();
        photoButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.i("MAIN Service", "User is about to take a picture with his camera");
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
                mBoundService.listKeystoreEntities();

            }
        });

        Button submitButton = (Button) this.findViewById(R.id.btnSubmit);
        submitButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.i("MAIN Service", "Validating image before sending to server...");
                if(mBoundService.compareHash(photo_data)) {
                    Log.i("MAIN Service", "Hash is the same");
                    Toast.makeText(MainActivity.this, R.string.sending_photo_to_server,
                            Toast.LENGTH_SHORT).show();
                }
                else
                    Log.i("MAIN Service", "Photo has been compromised");
            }
        });

        Button hackButton = (Button) this.findViewById(R.id.btnHack);
        hackButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                photo_data = photo_data + "a";
                Log.i("MAIN Service", "Manipulating image");
            }
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            photo_data =BitMapToString(photo);
            mBoundService.hashData(photo_data);
            Log.w("Main Service", "Sending data to daemon");
            imageView.setImageBitmap(photo);
            try {
                mBoundService.signData(photo_data);
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (UnrecoverableEntryException e) {
                e.printStackTrace();
            } catch (SignatureException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            }
        }
    }
    public String BitMapToString(Bitmap bitmap){
        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100, baos);
        byte [] b=baos.toByteArray();
        String temp = Base64.encodeToString(b, Base64.DEFAULT);
        return temp;
    }
    public Bitmap StringToBitMap(String encodedString){
        try {
            byte [] encodeByte=Base64.decode(encodedString,Base64.DEFAULT);
            Bitmap bitmap= BitmapFactory.decodeByteArray(encodeByte, 0, encodeByte.length);
            return bitmap;
        } catch(Exception e) {
            e.getMessage();
            return null;
        }
    }


    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        bindService(new Intent(MainActivity.this,
                DaemonService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }
}