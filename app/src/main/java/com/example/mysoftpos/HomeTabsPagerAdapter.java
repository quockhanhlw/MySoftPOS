package com.example.mysoftpos;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class HomeTabsPagerAdapter extends FragmentStateAdapter {

    public HomeTabsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return new OrdersFragment();
        if (position == 1) return new RevenueFragment();
        return new MyQrFragment();
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}

