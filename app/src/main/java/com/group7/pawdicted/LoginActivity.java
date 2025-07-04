package com.group7.pawdicted;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import java.util.*;

public class LoginActivity extends AppCompatActivity {
    private static final int RC_GOOGLE_SIGN_IN = 1001;
    private static final String TAG = "LOGIN";
    private static final String PREFS = "prefs";
    private static final String KEY_USER = "phone";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_REMEMBER = "remember";

    private EditText edtPhone, edtPassword;
    private CheckBox chkRemember;
    private ProgressBar progressBar;
    private View overlay;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CallbackManager fbCallbackManager;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_login);

        edtPhone = findViewById(R.id.edtPhone);
        edtPassword = findViewById(R.id.edtPassword);
        chkRemember = findViewById(R.id.ckbRememberInfo);
        progressBar = findViewById(R.id.progressBar);
        overlay = findViewById(R.id.overlay);
        findViewById(R.id.btnLogin).setOnClickListener(v -> loginWithPhone());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupGoogle();
        setupFacebook();
        loadPrefs();
    }

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (p.getBoolean(KEY_REMEMBER, false)) {
            edtPhone.setText(p.getString(KEY_USER, ""));
            edtPassword.setText(p.getString(KEY_PASSWORD, ""));
            chkRemember.setChecked(true);
        }
    }

    private void savePrefs(String phone, String password, boolean remember) {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        if (remember) {
            ed.putString(KEY_USER, phone);
            ed.putString(KEY_PASSWORD, password);
            ed.putBoolean(KEY_REMEMBER, true);
        } else {
            ed.remove(KEY_USER).remove(KEY_PASSWORD).remove(KEY_REMEMBER);
        }
        ed.apply();
    }

    private void loginWithPhone() {
        String phone = edtPhone.getText().toString().trim();
        String password = edtPassword.getText().toString();

        if (phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.title_fill_all_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
        db.collection("customers")
                .whereEqualTo("phone_number", phone)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        overlay.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.title_phone_not_found, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot userDoc = snapshot.getDocuments().get(0);
                    String email = userDoc.getString("customer_email");

                    if (email == null || email.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        overlay.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.title_invalid_email, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    mAuth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this, task -> {
                                if (task.isSuccessful()) {
                                    savePrefs(phone, password, chkRemember.isChecked());
                                    progressBar.setVisibility(View.GONE);
                                    overlay.setVisibility(View.GONE);
//                                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                                    navigateToHome();
                                } else {
                                    progressBar.setVisibility(View.GONE);
                                    overlay.setVisibility(View.GONE);
                                    Toast.makeText(this, R.string.title_login_failed, Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Login error", task.getException());
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    overlay.setVisibility(View.GONE);
//                    Toast.makeText(this, "Lỗi kết nối đến Firestore: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Firestore error", e);
                });
    }

    private void setupGoogle() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
        ((ImageButton) findViewById(R.id.imgGoogle)).setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
            googleSignInClient.signOut().addOnCompleteListener(t ->
                    startActivityForResult(googleSignInClient.getSignInIntent(), RC_GOOGLE_SIGN_IN)
            );
        });
    }

    private void setupFacebook() {
        fbCallbackManager = CallbackManager.Factory.create();
        ((ImageButton) findViewById(R.id.imgFacebook)).setOnClickListener(v -> {
            Toast.makeText(LoginActivity.this, R.string.feature_in_development, Toast.LENGTH_SHORT).show();
        });
    }

    private void facebookAuth(AccessToken token) {
        AuthCredential c = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(c).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                checkIfUserExistsAndProceed(mAuth.getCurrentUser());
            } else {
                progressBar.setVisibility(View.GONE);
                overlay.setVisibility(View.GONE);
                Toast.makeText(this, "Facebook login failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void googleAuth(String idToken) {
        progressBar.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
        AuthCredential c = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(c).addOnCompleteListener(t -> {
            if (t.isSuccessful()) {
                checkIfUserExistsAndProceed(mAuth.getCurrentUser());
            } else {
                progressBar.setVisibility(View.GONE);
                overlay.setVisibility(View.GONE);
                Toast.makeText(this, R.string.login_google_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkIfUserExistsAndProceed(FirebaseUser firebaseUser) {
        if (firebaseUser == null || firebaseUser.getEmail() == null) {
            progressBar.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
            Toast.makeText(this, R.string.login_account_verification_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        overlay.setVisibility(View.VISIBLE);
        db.collection("customers")
                .whereEqualTo("customer_email", firebaseUser.getEmail())
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        progressBar.setVisibility(View.GONE);
                        overlay.setVisibility(View.GONE);
                        Toast.makeText(this, R.string.login_account_not_registered, Toast.LENGTH_SHORT).show();
                        mAuth.signOut();
                    } else {
                        progressBar.setVisibility(View.GONE);
                        overlay.setVisibility(View.GONE);
                        navigateToHome();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    overlay.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.login_check_account_error + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onActivityResult(int rc, int rr, @Nullable Intent data) {
        super.onActivityResult(rc, rr, data);
        if (rc == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> t = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                googleAuth(t.getResult(ApiException.class).getIdToken());
            } catch (Exception e) {
                progressBar.setVisibility(View.GONE);
                overlay.setVisibility(View.GONE);
                Log.e(TAG, "Google error", e);
                Toast.makeText(this, R.string.login_google_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateToHome() {
        startActivity(new Intent(this, HomepageActivity.class));
        finish();
    }

    public void open_signup(View v) {
        startActivity(new Intent(this, SignupActivity.class));
    }

    public void open_forgot_password(View view) {
        startActivity(new Intent(this, ForgotPasswordActivity.class));
    }
}