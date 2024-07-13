package com.example.land2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener, SurfaceHolder.Callback, LocationListener {
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Sensor accelerometer, gyroscope, ahrs;

    private TextView accelTV, gyroTV, ahrsTV, gpsTV;
    private SurfaceView surfaceView;
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private boolean isCollectingData = false;

    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private File csvFile;
    private FileWriter csvWriter;

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accelTV = findViewById(R.id.accelerometerValuesTextView);
        gyroTV = findViewById(R.id.gyroscopeValuesTextView);
        ahrsTV = findViewById(R.id.ahrsValuesTextView);
        gpsTV = findViewById(R.id.GPStv);

        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            ahrs = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    }

    public void startRecording(View view) {
        if (!isRecording) {
            isCollectingData = true;
            try {
                mediaRecorder = new MediaRecorder();
                camera.unlock();
                mediaRecorder.setCamera(camera);

                mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

                mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

                File mediaStorageDir = new File( Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                        "MyCameraApp");
                if (!mediaStorageDir.exists()) {
                    if (!mediaStorageDir.mkdirs()) {
                        Toast.makeText(this, "Falha ao criar diretório de armazenamento", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String videoFileName = "sensor_data" + timeStamp + ".mp4";
                File mediaFile = new File(mediaStorageDir.getPath() + File.separator + videoFileName);

                mediaRecorder.setOutputFile(mediaFile.getAbsolutePath());

                // Inicia a gravação
                //camera.setDisplayOrientation(90);
                mediaRecorder.prepare();
                mediaRecorder.start();
                isRecording = true;

                // Inicia a gravação dos dados do acelerômetro no arquivo CSV
                File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                String filename = "sensor_data_" + dateFormat.format(new Date()) + ".csv";
                csvFile = new File(directory + File.separator +  filename);
                csvWriter = new FileWriter(csvFile);
                csvWriter.append("Sensor, Timestamp, Valor_X, Valor_Y, Valor_Z\n");
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
                sensorManager.registerListener(this, ahrs, SensorManager.SENSOR_DELAY_NORMAL);

                // Verificar e solicitar permissão de localização
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                    return;
                }
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, (LocationListener) this);


                Toast.makeText(this, "Gravação iniciada", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Erro ao iniciar a gravação", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void stopRecording(View view) {
        if (isRecording) {
            isCollectingData = false;
            // Para a gravação de vídeo
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
                camera.lock();
                isRecording = false;

                // Para a gravação dos dados do acelerômetro e fecha o arquivo CSV
                sensorManager.unregisterListener(this);
                csvWriter.flush();
                csvWriter.close();

                Toast.makeText(this, "Gravação encerrada", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Erro ao encerrar a gravação", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = Camera.open();
        try {
            setCameraDisplayOrientation();

            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // Não é necessário implementar
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.release();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isCollectingData) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            long time = System.currentTimeMillis();  //Para mostrar o tempo em milissegundos

            try {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    String line = String.format(Locale.getDefault(), "ACEL," + time + "," + x + "," + y + "," + z + "\n");
                    csvWriter.append(line);
                } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                    String line = String.format(Locale.getDefault(), "GYRO," + time + "," + x + "," + y + "," + z + "\n");
                    csvWriter.append(line);
                } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                    // Calcular os ângulos de Euler a partir dos dados do sensor AHRS
                    float[] rotationMatrix = new float[9];
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

                    // Remapear os ângulos de Euler para graus
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(rotationMatrix, orientation);

                    // Converter para graus e exibir os dados
                    float pitch = (float) Math.toDegrees(orientation[1]);
                    float roll = (float) Math.toDegrees(orientation[2]);
                    float yaw = (float) Math.toDegrees(orientation[0]);

                    String line = String.format(Locale.getDefault(), "AHRS," + time + "," + pitch + "," + roll + "," + yaw + "\n");
                    csvWriter.append(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            DecimalFormat df = new DecimalFormat("#.###");

            String xFormatado = df.format(x);
            String yFormatado = df.format(y);
            String zFormatado = df.format(z);

            String accelerometerValues = "Accelerometer:\n\tX: " + xFormatado + "\n\tY: " + yFormatado + "\n\tZ: " + zFormatado;
            accelTV.setText(accelerometerValues);
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            DecimalFormat df = new DecimalFormat("#.###");

            String xFormatado = df.format(x);
            String yFormatado = df.format(y);
            String zFormatado = df.format(z);

            String gyroscopeValues = "Gyroscope:\n\tX: " + xFormatado + "\n\tY: " + yFormatado + "\n\tZ: " + zFormatado;
            gyroTV.setText(gyroscopeValues);

        }else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

            // Remapear os ângulos de Euler para graus
            float[] orientation = new float[3];
            SensorManager.getOrientation(rotationMatrix, orientation);

            // Converter para graus e exibir os dados
            float pitch = (float) Math.toDegrees(orientation[1]);
            float roll = (float) Math.toDegrees(orientation[2]);
            float yaw = (float) Math.toDegrees(orientation[0]);

            DecimalFormat df = new DecimalFormat("#.###");

            String pitchFormatado = df.format(pitch);
            String rollFormatado = df.format(roll);
            String yawFormatado = df.format(yaw);

            String ahrsValues = "Pitch:\t\t\t Roll:\t\t\t\t Yaw:\n" + pitchFormatado + "\t\t " + rollFormatado + "\t\t" + yawFormatado;
            ahrsTV.setText(ahrsValues);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if(isCollectingData)
        {
            float latitude = (float) location.getLatitude();
            float longitude = (float) location.getLongitude();

            float speedMeterPerSecond = location.getSpeed();
            float speedKilometerPerHour = speedMeterPerSecond * 3.6f;

            long time = System.currentTimeMillis();  //Para mostrar o tempo em milissegundos

            String line = String.format(Locale.getDefault(), "GPS," + time + "," + latitude + "," + longitude + "," + speedKilometerPerHour + "\n");
            try {
                csvWriter.append(line);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            DecimalFormat df = new DecimalFormat("#.###");

            String latitudeFormatado = df.format(latitude);
            String longitudeFormatado = df.format(longitude);
            String speedFormatado = df.format(speedKilometerPerHour);

            String gpsValues = "Speed:\t\t\t Latitude:\t\t\t Longitude:\n "+ speedFormatado + "\t\t\t\t\t\t\t\t\t"  + latitudeFormatado + "\t\t\t\t\t" + longitudeFormatado;
            gpsTV.setText(gpsValues);
        }
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, cameraInfo);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cameraInfo.orientation + degrees) % 360;
            result = (360 - result) % 360; // Compensar a orientação espelhada da câmera frontal
        } else { // Camara traseira
            result = (cameraInfo.orientation - degrees + 360) % 360;
        }

        camera.setDisplayOrientation(result);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Não é necessário implementar
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        sensorManager.unregisterListener(this);
    }
}