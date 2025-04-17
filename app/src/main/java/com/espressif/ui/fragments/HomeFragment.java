package com.espressif.ui.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.espressif.mediwatch.R;
import com.espressif.ui.activities.mqtt_activities.DeviceConnectionChecker;
import com.espressif.ui.viewmodels.DispenserViewModel;

public class HomeFragment extends Fragment {
    
    // Estado de conexión del dispensador
    private ImageView ivDeviceStatus;
    private TextView tvDeviceStatus;
    private TextView tvDeviceDetails;
    
    // Verificador de conexión
    private DeviceConnectionChecker connectionChecker;
    
    // ViewModel
    private DispenserViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Inicializar ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(DispenserViewModel.class);
        
        // Configurar vistas
        setupViews(view);
        
        // Observar cambios en los datos
        observeViewModel();
    }
    
    @Override
    public void onStart() {
        super.onStart();
        // Iniciar verificación de conexión
        initConnectionChecker();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Liberar recursos del checker
        if (connectionChecker != null) {
            connectionChecker.release();
        }
    }
    
    private void setupViews(View view) {
        // Estado de conexión del dispensador
        ivDeviceStatus = view.findViewById(R.id.iv_device_status);
        tvDeviceStatus = view.findViewById(R.id.tv_device_status);
        tvDeviceDetails = view.findViewById(R.id.tv_device_details);
    }
    
    private void observeViewModel() {
        // Observar estado de conexión del dispensador
        viewModel.getDispenserConnected().observe(getViewLifecycleOwner(), connected -> {
            updateConnectionStatus(connected, viewModel.getDispenserStatus().getValue());
        });
        
        // Observar status del dispensador
        viewModel.getDispenserStatus().observe(getViewLifecycleOwner(), status -> {
            updateConnectionStatus(viewModel.getDispenserConnected().getValue(), status);
        });
    }
    
    private void updateConnectionStatus(Boolean connected, String status) {
        if (connected == null || status == null) {
            return;
        }
        
        if (connected) {
            ivDeviceStatus.setImageResource(R.drawable.ic_device_connected);
            ivDeviceStatus.setColorFilter(getResources().getColor(R.color.colorSuccess, null));
            tvDeviceStatus.setText("Dispensador conectado");
            tvDeviceDetails.setText(status);
        } else {
            ivDeviceStatus.setImageResource(R.drawable.ic_device_disconnected);
            ivDeviceStatus.setColorFilter(getResources().getColor(R.color.colorError, null));
            tvDeviceStatus.setText("Dispensador desconectado");
            tvDeviceDetails.setText("Verifica la conexión del dispositivo");
        }
    }
    
    private void initConnectionChecker() {
        if (connectionChecker == null) {
            connectionChecker = new DeviceConnectionChecker(requireContext());
        }
        
        // Iniciar primera verificación de conexión
        checkDeviceConnection();
        
        // Programar verificaciones periódicas (cada 30 segundos)
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && !isDetached()) {
                    checkDeviceConnection();
                    handler.postDelayed(this, 30000); // 30 segundos
                }
            }
        }, 30000); // Primera repetición en 30 segundos
    }

    private void checkDeviceConnection() {
        if (connectionChecker != null) {
            connectionChecker.checkConnection(new DeviceConnectionChecker.ConnectionCheckListener() {
                @Override
                public void onConnectionCheckResult(boolean isConnected) {
                    if (isAdded() && viewModel != null) {
                        viewModel.updateDispenserConnectionStatus(
                            isConnected, 
                            isConnected ? "Dispensador en línea" : "Dispensador desconectado"
                        );
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    if (isAdded() && viewModel != null) {
                        viewModel.updateDispenserConnectionStatus(false, "Error: " + errorMessage);
                    }
                }
            });
        }
    }
}