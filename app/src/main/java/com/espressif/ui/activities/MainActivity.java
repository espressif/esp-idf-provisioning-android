package com.espressif.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.espressif.mediwatch.R;
import com.espressif.ui.fragments.DispenserFragment;
import com.espressif.ui.fragments.HomeFragment;
import com.espressif.ui.fragments.SettingsFragment;
import com.espressif.ui.fragments.SmartwatchFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar BottomNavigationView
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this);
        
        // Por defecto, mostrar el fragmento de inicio
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
            bottomNavigationView.setSelectedItemId(R.id.navigation_home);
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        
        // Determinar qué fragmento cargar según el item seleccionado
        int itemId = item.getItemId();
        if (itemId == R.id.navigation_home) {
            fragment = new HomeFragment();
        } else if (itemId == R.id.navigation_dispenser) {
            fragment = new DispenserFragment();
        } else if (itemId == R.id.navigation_smartwatch) {
            fragment = new SmartwatchFragment();
        } else if (itemId == R.id.navigation_settings) {
            fragment = new SettingsFragment();
        }
        
        // Cargar el fragmento seleccionado
        return loadFragment(fragment);
    }
    
    /**
     * Método para cargar un fragmento en el contenedor
     */
    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();
            return true;
        }
        return false;
    }
}