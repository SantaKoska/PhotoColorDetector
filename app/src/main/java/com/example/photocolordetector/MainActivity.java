package com.example.photocolordetector;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.annotations.SerializedName;  // Make sure this is available after adding the Gson dependency
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView colorName, hexCode;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private Bitmap bitmap;

    private ColorApi colorApi;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.image_view);
        colorName = findViewById(R.id.color_name);
        hexCode = findViewById(R.id.hex_code);
        Button selectImageButton = findViewById(R.id.button_select_image);

        // Initialize Retrofit
        initRetrofit();

        // Gallery launcher for selecting an image
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        imageView.setImageURI(imageUri);

                        // Load the bitmap from the selected image
                        try {
                            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
        );

        // Set onClickListener to open gallery
        selectImageButton.setOnClickListener(v -> openGallery());

        // Set an onTouchListener to detect taps on the ImageView
        imageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (bitmap != null) {
                    float touchX = event.getX();
                    float touchY = event.getY();
                    float scaleX = (float) bitmap.getWidth() / imageView.getWidth();
                    float scaleY = (float) bitmap.getHeight() / imageView.getHeight();
                    int bitmapX = (int) (touchX * scaleX);
                    int bitmapY = (int) (touchY * scaleY);

                    if (bitmapX >= 0 && bitmapX < bitmap.getWidth() &&
                            bitmapY >= 0 && bitmapY < bitmap.getHeight()) {
                        int pixelColor = bitmap.getPixel(bitmapX, bitmapY);
                        int red = Color.red(pixelColor);
                        int green = Color.green(pixelColor);
                        int blue = Color.blue(pixelColor);

                        sendColorToServer(new int[]{red, green, blue});
                    }
                }
            }
            return true;
        });
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void initRetrofit() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(interceptor).build();

        // Ensure the base URL ends with a '/'
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://10.0.2.2:5000/")  // Use 10.0.2.2 for emulator access to localhost
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        colorApi = retrofit.create(ColorApi.class);
    }

    private void sendColorToServer(int[] rgb) {
        ColorRequest request = new ColorRequest(rgb);
        colorApi.getColor(request).enqueue(new Callback<ColorResponse>() {
            @Override
            public void onResponse(Call<ColorResponse> call, Response<ColorResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    colorName.setText(response.body().getColorName());
                    hexCode.setText(response.body().getHexCode());
                } else {
                    Toast.makeText(MainActivity.this, "Failed to get response", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ColorResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Retrofit API interface
    interface ColorApi {
        @POST("/")  // Match the root endpoint of your Flask app
        Call<ColorResponse> getColor(@Body ColorRequest request);
    }

    // Request and Response model classes
    public static class ColorRequest {
        private int[] rgb;

        public ColorRequest(int[] rgb) {
            this.rgb = rgb;
        }
    }

    public static class ColorResponse {
        @SerializedName("color_name")
        private String colorName;
        @SerializedName("hex_code")
        private String hexCode;

        public String getColorName() {
            return colorName;
        }

        public String getHexCode() {
            return hexCode;
        }
    }
}
