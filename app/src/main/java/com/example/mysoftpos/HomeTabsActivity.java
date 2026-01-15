package com.example.mysoftpos;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class HomeTabsActivity extends AppCompatActivity {

    public static final String EXTRA_INITIAL_TAB = "initial_tab";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_tabs);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        HomeTabsPagerAdapter adapter = new HomeTabsPagerAdapter(this);
        viewPager.setAdapter(adapter);
        viewPager.setOffscreenPageLimit(3);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            if (position == 0) {
                tab.setText(R.string.tab_orders);
            } else if (position == 1) {
                tab.setText(R.string.tab_revenue);
            } else {
                tab.setText(R.string.tab_my_qr);
            }
        }).attach();

        int initialTab = getIntent().getIntExtra(EXTRA_INITIAL_TAB, 0);
        if (initialTab < 0 || initialTab > 2) initialTab = 0;

        // Set initial item without animation.
        viewPager.setCurrentItem(initialTab, false);

        // Make pill background behave per-tab (selected state).
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(@NonNull TabLayout.Tab tab) {
                tab.view.setSelected(true);
            }

            @Override
            public void onTabUnselected(@NonNull TabLayout.Tab tab) {
                tab.view.setSelected(false);
            }

            @Override
            public void onTabReselected(@NonNull TabLayout.Tab tab) {
                tab.view.setSelected(true);
            }
        });

        // Ensure initial selected state applied after mediator attach.
        TabLayout.Tab tab = tabLayout.getTabAt(initialTab);
        if (tab != null) {
            tab.view.setSelected(true);
        }
    }
}
