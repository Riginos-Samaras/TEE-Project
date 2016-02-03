package com.cei522.trustedphotocapture.myapplication;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.util.List;
import android.os.Build;
import android.os.Binder;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.security.KeyChain;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Enumeration;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import java.util.Random;

public class DaemonService extends Service {
    KeyStore keyStore;
    List<String> keyAliases;
    private NotificationManager mNM;
    private int photo_hash;
    private byte[] sign_hash;
    // Random number generator
    private final Random mGenerator = new Random();

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.daemon_service_started;
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        DaemonService getService() {
            return DaemonService.this;
        }
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        try {
            generateKeys();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (SignatureException e) {
            e.printStackTrace();
        } catch (UnrecoverableEntryException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("Daemon Service", "Received start id " + startId + ": " + intent);
       // Toast.makeText(this, R.string.daemon_service_start_command_message, Toast.LENGTH_SHORT).show();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        mNM.cancel(NOTIFICATION);

        // Tell the user we stopped.
        Toast.makeText(this, R.string.daemon_service_stoped, Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // In this sample, we'll use the same text for the ticker and the expanded notification
        CharSequence text = getText(R.string.daemon_service_started);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        Notification notification = new Notification.Builder(this)
                .setSmallIcon(R.drawable.icon)  // the status icon
                .setTicker(text)  // the status text
                .setWhen(System.currentTimeMillis())  // the time stamp
                .setContentTitle(getText(R.string.daemon_service_label))  // the label of the entry
                .setContentText(text)  // the contents of the entry
                .setContentIntent(contentIntent)  // The intent to send when the entry is clicked
                .build();

        // Send the notification.
        mNM.notify(NOTIFICATION, notification);
    }

    /** method for clients */

    public void hashData(String data) {

        photo_hash = data.hashCode();

    }

    public boolean compareHash(String data){

        if (data.hashCode() == photo_hash)
            return true;
        else
            return false;
    }

    private String BitMapToString(Bitmap bitmap){
        ByteArrayOutputStream baos=new  ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG,100, baos);
        byte [] b=baos.toByteArray();
        String temp = Base64.encodeToString(b, Base64.DEFAULT);
        return temp;
    }

    private void generateKeys() throws NoSuchProviderException, NoSuchAlgorithmException, CertificateException, InvalidKeyException, KeyStoreException, SignatureException, UnrecoverableEntryException, IOException {
        KeyPairGenerator kpg = null;
        Log.i("Daemon Service", "Generating Keys");
        try {
            kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
        }
        try {
            kpg.initialize(new KeyGenParameterSpec.Builder(
                    "ReCRED",
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                    .setDigests(KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA512)
                    .build());
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }

        KeyPair kp = kpg.generateKeyPair();
        PrivateKey pkey = kp.getPrivate();
        PublicKey public_key=kp.getPublic();

        SecretKeyFactory factory = null;
        try {
            factory = SecretKeyFactory.getInstance(pkey.getAlgorithm(), "AndroidKeyStore");
        } catch (NoSuchAlgorithmException e1) {
            e1.printStackTrace();
        } catch (NoSuchProviderException e1) {
            e1.printStackTrace();
        }
        Log.i("Daemon Service", "Querying Keystore to check if the key is inside hardware");
        KeyFactory factory2 = KeyFactory.getInstance(pkey.getAlgorithm(), "AndroidKeyStore");
        KeyInfo keyInfo = null;
        try {
            keyInfo = factory2.getKeySpec(pkey, KeyInfo.class);
            Log.i("Daemon Service", "Is on Hardware = " + keyInfo.isInsideSecureHardware());
        } catch (InvalidKeySpecException e) {
            // Not an Android KeyStore key.
        }


    }

    public void listKeystoreEntities() {
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        try {
            ks.load(null);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        }
        Enumeration<String> aliases = null;
        try {
            aliases = ks.aliases();
            Log.i("Daemon Service", "Keystore Aliases " + java.util.Arrays.asList(aliases.nextElement()));
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }



    }

    public void signData(String message) throws CertificateException, NoSuchAlgorithmException, UnrecoverableEntryException, SignatureException, InvalidKeyException, IOException, KeyStoreException {
        Signature signature = Signature.getInstance("SHA256withRSA/PSS");
        Log.i("Daemon Service", "INFO: signData() started");
        KeyStore ks = null;
        try {
            ks = KeyStore.getInstance("AndroidKeyStore");
        } catch (KeyStoreException e) {
            e.printStackTrace();
            Log.i("Daemon Service", "ERROR 1");
        }
        ks.load(null);

       // KeyStore.PrivateKeyEntry privateKeyEntry = null;
        KeyStore.ProtectionParameter protParam =
                new KeyStore.PasswordProtection(null);
        KeyStore.Entry entry = ks.getEntry("ReCRED", protParam);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            Log.i("Daemon Service", "Not an instance of a PrivateKeyEntry");
        }
        Log.i("Daemon Service", "INFO: KeyStore Entry has been created");
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) entry;

        try{
            signature.initSign(privateKeyEntry.getPrivateKey());
        } catch ( Exception e) {
//            Log.i("Daemon Service", "ERROR initSign");
//        e.printStackTrace();


        }

        Log.i("Daemon Service", "INFO: Initialized signiture");

        byte[] data = new byte[0];
        try {
            data = message.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        Log.i("Daemon Service", "Converting data to bytestring:" + data);
        try {
            signature.update(data);
        } catch (SignatureException e) {
            e.printStackTrace();
        }

        byte[] signatureBytes = new byte[0];
        try {
            signatureBytes = signature.sign();
        } catch (SignatureException e) {
            e.printStackTrace();
        }
        Log.i("Daemon Service", "Sign Data:" + signatureBytes);
        boolean verified = false;
        try {
            verified = verifyData(data,signatureBytes);
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }
        Log.i("Daemon Service", "Verified " + verified);
        sign_hash = signatureBytes;
    }

    private boolean verifyData(byte[] data, byte[] signatureBytes) throws InvalidKeyException, KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableEntryException, SignatureException {
        Log.i("Daemon Service", "Executing verification");
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry("ReCRED", null);
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            Log.i("Daemon Service", "Not an instance of a PrivateKeyEntry");
            return false;
        }
        Signature s = Signature.getInstance("SHA256withRSA/PSS");
        try {
            s.initVerify(((KeyStore.PrivateKeyEntry) entry).getCertificate());
        } catch (InvalidKeyException e){
//            e.printStackTrace();
//            Log.i("Daemon Service", "ERROR initVerify");

        }

        s.update(data);
        boolean valid = s.verify(signatureBytes);
        return true;
    }

    public byte[] getHash(){

        return sign_hash;

    }

}
