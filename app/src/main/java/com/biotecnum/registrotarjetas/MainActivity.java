package com.biotecnum.registrotarjetas;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // NFC
    NfcAdapter nfcAdapter;
    IntentFilter intentFilter;
    private Tag tag;


    // Firebase
    private DatabaseReference mDatabaseT, mDatabase, mConditionRef;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private String correo = "registrotarjetas@sicpe.com", pass = "registroTarjetasSIPE";

    //
    private TextView tvTotal, tvTagId;
    private String id;
    private double T;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTotal = (TextView)findViewById(R.id.tvTotal);
        tvTagId = (TextView)findViewById(R.id.tvTagId);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        intentFilter = new IntentFilter();

        mAuth = FirebaseAuth.getInstance(); // important Call
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Toast.makeText(MainActivity.this, "Usuario ya inició sesión ", Toast.LENGTH_SHORT).show();
                }else{
                    callsignin(correo, pass);
                }
            }
        };
    }

    private void callsignin(String email,String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                    if (!task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Falló el inicio de sesión", Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Toast.makeText(MainActivity.this, "Inicio de Sesión correcto", Toast.LENGTH_SHORT).show();
                    }
                    }
                });

    }


    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);

        mConditionRef = FirebaseDatabase.getInstance().getReference().child("rt/totalTarjetas");
        mConditionRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                InfoTotal infoTotalTar = dataSnapshot.getValue(InfoTotal.class);

                if(infoTotalTar != null){

                    double tarjetas = infoTotalTar.total;
                    tvTotal.setText("Total: " + new BigDecimal(tarjetas).toString());
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }



    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if(intent.hasExtra(NfcAdapter.EXTRA_TAG)){

            tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            byte a[] = tag.getId();
            id = ByteArrayToHexString(a);

            mDatabaseT = FirebaseDatabase.getInstance().getReference().child("rt/tarjetas/"+id);
            mDatabaseT.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {

                    InfoTarjeta infoT = dataSnapshot.getValue(InfoTarjeta.class);

                    if(infoT == null) {

                        tvTagId.setText(id);
                        mDatabaseT.child("registro").setValue(true);

                        ///////////
                        mDatabase = FirebaseDatabase.getInstance().getReference().child("rt/totalTarjetas");
                        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {

                                InfoTotal infoTotal = dataSnapshot.getValue(InfoTotal.class);

                                if(infoTotal != null){


                                    T = infoTotal.total + 1;
                                    mDatabase.child("total").setValue(T);

                                    tvTagId.setText("Por favor ingrese una nueva tarjeta");
                                }


                            }

                            @Override
                            public void onCancelled(DatabaseError databaseError) {

                            }
                        });
                        ///////////

                        Toast.makeText(MainActivity.this, "Registro Exitoso", Toast.LENGTH_SHORT).show();



                    }else{
                        tvTagId.setText("Por favor ingrese una nueva tarjeta");
                        Toast.makeText(MainActivity.this, "Tarjeta ya registrada", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });

        }
    }


    public static String ByteArrayToHexString(byte[] bytes) {
        final char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for ( int j = 0; j < bytes.length; j++ ) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableForegroundDispatchSystem();
    }

    @Override
    protected void onPause() {
        super.onPause();
        disableForegroundDispatchSystem();
    }

    private void enableForegroundDispatchSystem(){
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        IntentFilter[] intentFilter = new IntentFilter[]{};

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFilter, null);

    }

    private void disableForegroundDispatchSystem(){
        nfcAdapter.disableForegroundDispatch(this);
    }

    private void formatTag(Tag tag, NdefMessage ndefMessage){

        try{
            NdefFormatable ndefFormatable = NdefFormatable.get(tag);

            if (ndefFormatable == null){
                Toast.makeText(this, "Tag is not ndef Formatable!", Toast.LENGTH_SHORT).show();
            }

            ndefFormatable.connect();
            ndefFormatable.format(ndefMessage);
            ndefFormatable.close();

            Toast.makeText(this,"Tag Writen!", Toast.LENGTH_SHORT).show();

        }catch (Exception e) {
            Log.e("formatTag", e.getMessage());
        }

    }

}
