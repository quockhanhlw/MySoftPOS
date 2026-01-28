package com.example.mysoftpos.testsuite;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mysoftpos.R;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * Screen 1: Scheme Selection - Home screen with card scheme grid.
 */
public class SchemeSelectActivity extends AppCompatActivity {

    private RecyclerView rvSchemes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheme_select);

        rvSchemes = findViewById(R.id.rvSchemes);
        rvSchemes.setLayoutManager(new GridLayoutManager(this, 2));
        
        List<String> schemes = TestDataProvider.getSchemes();
        rvSchemes.setAdapter(new SchemeAdapter(schemes));
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         SCHEME ADAPTER
    // ═══════════════════════════════════════════════════════════════════
    
    private class SchemeAdapter extends RecyclerView.Adapter<SchemeAdapter.ViewHolder> {
        private final List<String> schemes;

        SchemeAdapter(List<String> schemes) {
            this.schemes = schemes;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scheme_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String scheme = schemes.get(position);
            holder.tvSchemeName.setText(scheme);
            
            // Set logo based on scheme
            int logoRes = getSchemeLogoRes(scheme);
            holder.ivSchemeLogo.setImageResource(logoRes);
            
            holder.cardScheme.setOnClickListener(v -> {
                Intent intent = new Intent(SchemeSelectActivity.this, ChannelSelectActivity.class);
                intent.putExtra("SCHEME", scheme);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return schemes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView cardScheme;
            ImageView ivSchemeLogo;
            TextView tvSchemeName;

            ViewHolder(View itemView) {
                super(itemView);
                cardScheme = itemView.findViewById(R.id.cardScheme);
                ivSchemeLogo = itemView.findViewById(R.id.ivSchemeLogo);
                tvSchemeName = itemView.findViewById(R.id.tvSchemeName);
            }
        }
        
        private int getSchemeLogoRes(String scheme) {
            switch (scheme.toUpperCase()) {
                case "NAPAS": return R.drawable.ic_nfc;
                case "VISA": return R.drawable.ic_nfc;
                case "MASTERCARD": return R.drawable.ic_nfc;
                case "JCB": return R.drawable.ic_nfc;
                default: return R.drawable.ic_nfc;
            }
        }
    }
}
