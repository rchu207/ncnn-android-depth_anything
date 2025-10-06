// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.dpt;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.heifwriter.HeifWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

class BitmapSaver {

    public static File saveBitmapToFile(Bitmap bitmap, File file, Bitmap.CompressFormat format, int quality) throws IOException {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            bitmap.compress(format, quality, out); // Compress the bitmap to the output stream
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return file;
    }

    // Example usage:
    public static void saveImage(Bitmap bitmap, String filename) {
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyAppImages");
        if (!directory.exists()) {
            directory.mkdirs(); // Create the directory if it doesn't exist
        }

        File imageFile = new File(directory, filename);
        try {
            saveBitmapToFile(bitmap, imageFile, Bitmap.CompressFormat.PNG, 100); // Save as PNG with 100% quality
            // Optionally, you can add the image to MediaStore for it to appear in the gallery
            // MediaStore.Images.Media.insertImage(context.getContentResolver(), imageFile.getAbsolutePath(), filename, null);
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the error (e.g., show a Toast message)
        }
    }
}

class HeifSaver {
    // Example usage:
    public static void saveImage(Bitmap bitmap1, Bitmap bitmap2, String filename) {
        File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyAppImages");
        if (!directory.exists()) {
            directory.mkdirs(); // Create the directory if it doesn't exist
        }

        File imageFile = new File(directory, filename);
        HeifWriter.Builder builder = new HeifWriter.Builder(imageFile.getAbsolutePath(), bitmap1.getWidth(), bitmap1.getHeight(), HeifWriter.INPUT_MODE_BITMAP);
        HeifWriter writer = null;
        try {
            builder.setMaxImages(2);
            writer = builder.build();
            writer.start();
            writer.addBitmap(bitmap1);
            writer.addBitmap(bitmap2);
            writer.stop(0);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

public class MainActivity extends Activity {
    private static final int SELECT_IMAGE = 1;

    private ImageView imageView;
    private Bitmap yourSelectedImage = null;

    private final Dpt dpt = new Dpt();

    private int current_model = 0;
    private int current_cpugpu = 0;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        imageView = findViewById(R.id.imageView);

        Button buttonImage = findViewById(R.id.buttonImage);
        buttonImage.setOnClickListener(arg0 -> {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setType("image/*");
            startActivityForResult(i, SELECT_IMAGE);
        });

        Button buttonDetect = findViewById(R.id.buttonDetect);
        buttonDetect.setOnClickListener(arg0 -> {
            if (yourSelectedImage == null)
                return;

            // TODO: run inference in thread.
            Bitmap bitmap = yourSelectedImage.copy(Bitmap.Config.ARGB_8888, true);
            dpt.infer(bitmap);
            imageView.setImageBitmap(bitmap);
            BitmapSaver.saveImage(bitmap, "out_heatmap.png");
            HeifSaver.saveImage(yourSelectedImage, bitmap, "out_heatmap.heic");
        });

        Spinner spinnerModel = findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (position != current_model) {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        Spinner spinnerCPUGPU = findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id) {
                if (position != current_cpugpu) {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        reload();
    }

    private void reload() {
        boolean ret_init = dpt.loadModel(getAssets(), current_model, current_cpugpu);
        if (!ret_init) {
            Log.e("MainActivity", "dpt loadModel failed");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && null != data) {
            Uri selectedImage = data.getData();

            try {
                if (requestCode == SELECT_IMAGE && selectedImage != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, null);
                    if (bitmap != null) {
                        yourSelectedImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        imageView.setImageBitmap(bitmap);
                    }
                }
            } catch (FileNotFoundException e) {
                Log.e("MainActivity", "FileNotFoundException");
            }
        }
    }
}
