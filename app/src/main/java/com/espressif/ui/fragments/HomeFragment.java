package com.espressif.ui.fragments;

import android.graphics.Color;
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
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.PercentFormatter;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    
    // Estado de conexión del dispensador
    private ImageView ivDeviceStatus;
    private TextView tvDeviceStatus;
    private TextView tvDeviceDetails;
    
    // Gráficos de compartimentos
    private PieChart chartCompartmentA;
    private PieChart chartCompartmentB;
    private PieChart chartCompartmentC;
    private PieChart chartCompartmentLiquid;
    
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
        
        // Configurar gráficos
        setupCharts();
        
        // Observar cambios en los datos
        observeViewModel();
        
        // Usar getSelectedPatientId() en lugar de getPatientId()
        String patientId = viewModel.getSelectedPatientId();
        if (patientId != null) {
            viewModel.loadMedications(patientId);
        }
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
    
    @Override
    public void onResume() {
        super.onResume();
        
        // Forzar una actualización de los datos cuando el fragmento se vuelve visible
        // Usar getSelectedPatientId() en lugar de getPatientId()
        if (viewModel != null) {
            String patientId = viewModel.getSelectedPatientId();
            if (patientId != null) {
                viewModel.loadMedications(patientId);
            }
        }
        
        // También podemos programar actualizaciones periódicas
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && !isDetached() && viewModel != null) {
                    String patientId = viewModel.getSelectedPatientId();
                    if (patientId != null) {
                        viewModel.loadMedications(patientId);
                    }
                    handler.postDelayed(this, 60000); // Actualizar cada minuto
                }
            }
        }, 60000);
    }
    
    private void setupViews(View view) {
        // Estado de conexión del dispensador
        ivDeviceStatus = view.findViewById(R.id.iv_device_status);
        tvDeviceStatus = view.findViewById(R.id.tv_device_status);
        tvDeviceDetails = view.findViewById(R.id.tv_device_details);
        
        // Gráficos
        chartCompartmentA = view.findViewById(R.id.chart_compartment_a);
        chartCompartmentB = view.findViewById(R.id.chart_compartment_b);
        chartCompartmentC = view.findViewById(R.id.chart_compartment_c);
        chartCompartmentLiquid = view.findViewById(R.id.chart_compartment_liquid);
    }
    
    private void setupCharts() {
        setupPieChart(chartCompartmentA, "2/4");
        setupPieChart(chartCompartmentB, "1/3");
        setupPieChart(chartCompartmentC, "0/2");
        setupPieChart(chartCompartmentLiquid, "150/300");
    }
    
    private void setupPieChart(PieChart chart, String centerText) {
        // Configuración básica
        chart.setUsePercentValues(true);
        chart.setCenterText(centerText);
        chart.setCenterTextSize(16f);
        
        // Eliminar descripción y leyenda
        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);
        chart.getLegend().setEnabled(false);
        
        // Configurar apariencia del gráfico
        chart.setHoleRadius(70f);
        chart.setTransparentCircleRadius(75f);
        chart.setDrawEntryLabels(false);
        chart.setRotationEnabled(false);
        chart.setHighlightPerTapEnabled(false);
    }
    
    private void updateChartData(PieChart chart, float taken, float remaining, String centerText) {
        List<PieEntry> entries = new ArrayList<>();
        
        // Añadir entradas si hay valores positivos
        if (taken > 0) entries.add(new PieEntry(taken, "Tomadas"));
        if (remaining > 0) entries.add(new PieEntry(remaining, "Pendientes"));
        
        // Si ambos son cero, mostrar 100% pendientes
        if (entries.isEmpty()) {
            entries.add(new PieEntry(1, "Pendientes"));
        }
        
        PieDataSet dataSet = new PieDataSet(entries, "");
        
        // Colores: azul para tomadas, gris claro para pendientes
        ArrayList<Integer> colors = new ArrayList<>();
        colors.add(Color.parseColor("#4285F4")); // Azul para tomadas
        colors.add(Color.parseColor("#E0E0E0")); // Gris claro para pendientes
        dataSet.setColors(colors);
        
        PieData data = new PieData(dataSet);
        data.setValueFormatter(new PercentFormatter(chart));
        data.setValueTextSize(12f);
        data.setValueTextColor(Color.WHITE);
        
        // Actualizar datos y texto central
        chart.setData(data);
        chart.setCenterText(centerText);
        chart.invalidate(); // Refrescar
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
        
        // Observar datos de los compartimentos
        viewModel.getCompartmentA().observe(getViewLifecycleOwner(), data -> {
            updateChartData(chartCompartmentA, data.getTaken(), data.getRemaining(), 
                    data.getTaken() + "/" + data.getTotal());
        });
        
        viewModel.getCompartmentB().observe(getViewLifecycleOwner(), data -> {
            updateChartData(chartCompartmentB, data.getTaken(), data.getRemaining(), 
                    data.getTaken() + "/" + data.getTotal());
        });
        
        viewModel.getCompartmentC().observe(getViewLifecycleOwner(), data -> {
            updateChartData(chartCompartmentC, data.getTaken(), data.getRemaining(), 
                    data.getTaken() + "/" + data.getTotal());
        });
        
        viewModel.getCompartmentLiquid().observe(getViewLifecycleOwner(), data -> {
            updateChartData(chartCompartmentLiquid, data.getTaken(), data.getRemaining(), 
                    data.getTaken() + "/" + data.getTotal());
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
    
    // Método temporal para simular datos de los gráficos
    private void simulateChartData() {
        // Compartimento A: 2 de 4 pastillas tomadas (50%)
        updateChartData(chartCompartmentA, 2, 2, "2/4");
        
        // Compartimento B: 1 de 3 pastillas tomadas (33%)
        updateChartData(chartCompartmentB, 1, 2, "1/3");
        
        // Compartimento C: 0 de 2 pastillas tomadas (0%)
        updateChartData(chartCompartmentC, 0, 2, "0/2");
        
        // Compartimento Líquido: 150 de 300 ml consumidos (50%)
        updateChartData(chartCompartmentLiquid, 150, 150, "150/300");
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