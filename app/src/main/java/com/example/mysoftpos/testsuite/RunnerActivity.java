package com.example.mysoftpos.testsuite;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.mysoftpos.R;
import com.example.mysoftpos.testsuite.model.CardProfile;
import com.example.mysoftpos.testsuite.model.TestCase;
import com.example.mysoftpos.testsuite.model.TestResult;
import com.example.mysoftpos.testsuite.viewmodel.RunnerViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * PRODUCTION-GRADE RUNNER VIEW.
 * - MVVM Pattern (Observes ViewModel).
 * - Passive View (No Business Logic).
 * - SOLID Compliant.
 */
public class RunnerActivity extends AppCompatActivity {

    private static final String PREF_NAME = "app_settings";
    private static final String KEY_THEME = "theme_mode";

    private RunnerViewModel viewModel;

    // UI Components
    private AutoCompleteTextView actvTestCase, actvCardProfile;
    private TextInputEditText etAmount;
    private MaterialButton btnRun;
    private ImageButton btnThemeSwitch;
    private MaterialCardView cvResult;
    private View layoutLoading;
    
    // Result Views
    private TextView tvResultTitle, tvRc, tvRrn;
    private ImageView ivResultIcon;

    // Selection State
    private TestCase selectedTestCase;
    private CardProfile selectedCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_runner);

        // 1. Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(RunnerViewModel.class);

        // 2. Setup UI
        initViews();
        setupDropdowns();
        setupListeners();
        
        // 3. Observe State
        observeViewModel();
    }
    
    private void initViews() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> finish());

        actvTestCase = findViewById(R.id.actvTestCase);
        actvCardProfile = findViewById(R.id.actvCardProfile);
        etAmount = findViewById(R.id.etAmount);
        btnRun = findViewById(R.id.btnRun);
        btnThemeSwitch = findViewById(R.id.btnThemeSwitch);
        cvResult = findViewById(R.id.cvResult);
        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvRc = findViewById(R.id.tvRc);
        tvRrn = findViewById(R.id.tvRrn);
        ivResultIcon = findViewById(R.id.ivResultIcon);
        layoutLoading = findViewById(R.id.layoutLoading);
    }

    private void setupDropdowns() {
        // Load Data - Using Custom Layout for Stability
        List<TestCase> testCases = TestDataProvider.getPosTestCases(); 
        testCases.addAll(TestDataProvider.getAtmTestCases());
        testCases.addAll(TestDataProvider.getQrcTestCases());

        ArrayAdapter<TestCase> caseAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown_testcase, testCases);
        if (actvTestCase != null) actvTestCase.setAdapter(caseAdapter);

        // Cards
        List<CardProfile> testCards = TestDataProvider.getTestCards();
        ArrayAdapter<CardProfile> cardAdapter = new ArrayAdapter<>(this, R.layout.item_dropdown_testcase, testCards);
        if (actvCardProfile != null) actvCardProfile.setAdapter(cardAdapter);
    }

    private void setupListeners() {
        if (actvTestCase != null) {
            actvTestCase.setOnItemClickListener((parent, view, position, id) -> selectedTestCase = (TestCase) parent.getItemAtPosition(position));
        }
        if (actvCardProfile != null) {
            actvCardProfile.setOnItemClickListener((parent, view, position, id) -> selectedCard = (CardProfile) parent.getItemAtPosition(position));
        }
        
        if (btnRun != null) btnRun.setOnClickListener(v -> handleRunClick());
        if (btnThemeSwitch != null) btnThemeSwitch.setOnClickListener(v -> toggleTheme());
    }

    private void observeViewModel() {
        viewModel.getState().observe(this, state -> {
            if (state instanceof RunnerViewModel.RunnerState.Loading) {
                showLoading(true);
            } else if (state instanceof RunnerViewModel.RunnerState.Success) {
                showLoading(false);
                showResult(((RunnerViewModel.RunnerState.Success) state).result);
            } else if (state instanceof RunnerViewModel.RunnerState.Error) {
                showLoading(false);
                Toast.makeText(this, "Error: " + ((RunnerViewModel.RunnerState.Error) state).message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleRunClick() {
        if (selectedTestCase == null || selectedCard == null) {
            Toast.makeText(this, "Please select Test Case and Card", Toast.LENGTH_SHORT).show();
            return;
        }
        String amount = etAmount.getText() != null ? etAmount.getText().toString() : "0";
        // Delegate to ViewModel
        viewModel.runTransaction(selectedTestCase, selectedCard, amount);
    }

    private void showResult(TestResult result) {
        if (cvResult == null) return;
        cvResult.setVisibility(View.VISIBLE);
        
        if (tvRc != null) tvRc.setText(result.responseCode);
        if (tvRrn != null) tvRrn.setText(result.rrn);
        
        boolean success = "SUCCESS".equals(result.status);
        if (tvResultTitle != null) {
            tvResultTitle.setText(success ? "APPROVED" : "DECLINED (" + result.responseCode + ")");
            int color = success ? ContextCompat.getColor(this, R.color.status_success) 
                                : ContextCompat.getColor(this, R.color.status_error);
            tvResultTitle.setTextColor(color);
            if (ivResultIcon != null) {
                ivResultIcon.setColorFilter(color);
                ivResultIcon.setImageResource(success ? R.drawable.ic_check_circle_24 : R.drawable.ic_warning_24);
            }
        }
    }

    private void showLoading(boolean show) {
        if (layoutLoading != null) layoutLoading.setVisibility(show ? View.VISIBLE : View.GONE);
        if (btnRun != null) btnRun.setEnabled(!show);
    }
    
    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode);
        }
    }

    private void toggleTheme() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        int currentMode = AppCompatDelegate.getDefaultNightMode();
        int newMode = (currentMode == AppCompatDelegate.MODE_NIGHT_YES) ? 
                      AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        prefs.edit().putInt(KEY_THEME, newMode).apply();
        AppCompatDelegate.setDefaultNightMode(newMode);
    }
}
