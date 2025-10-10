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
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.media.MediaCodec;
import android.media.MediaFormat;
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
import java.nio.ByteBuffer;
import java.util.List;

import com.nokia.heif.AuxiliaryProperty;
import com.nokia.heif.GridImageItem;
import com.nokia.heif.HEIF;
import com.nokia.heif.HEVCImageItem;
import com.nokia.heif.ImageItem;
import com.nokia.heif.Item;
import com.nokia.heif.ItemProperty;
import com.nokia.heif.io.ByteArrayInputStream;
import com.nokia.heif.io.InputStream;

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
    private static String TAG = "NcnnActivity";
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

            // Decode HEIC by BitmapFactory.
            try {
                if (requestCode == SELECT_IMAGE && selectedImage != null) {
                    BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImage), null, null);
                }
            } catch (FileNotFoundException e) {
                Log.e("MainActivity", "FileNotFoundException");
            }

            // Decode HEIC by ImageDecoder.
            if (requestCode == SELECT_IMAGE && selectedImage != null) {
                ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), selectedImage);
                try {
                    Bitmap bitmap = ImageDecoder.decodeBitmap(src);
                    yourSelectedImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    imageView.setImageBitmap(bitmap);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // Decode HEIC by Nokia HEIF library.
            HEIF heif = new HEIF();
            try {
                // Load the file
                byte[] inputBuffer = getContentResolver().openInputStream(selectedImage).readAllBytes();
                com.nokia.heif.io.ByteArrayInputStream nokiaInputStream = new ByteArrayInputStream(inputBuffer);
                heif.load(nokiaInputStream);

                // Get the primary image
                ImageItem primaryImage = heif.getPrimaryImage();

                Log.e(TAG, "Images=" + heif.getImages().size());

                // Check the type, assuming that it's a HEVC image
                if (primaryImage instanceof HEVCImageItem) {
                    HEVCImageItem hevcImageItem = (HEVCImageItem)primaryImage;
                    byte[] decoderConfig = hevcImageItem.getDecoderConfig().getConfig();
                    byte[] imageData = hevcImageItem.getItemDataAsArray();
                    // Feed the data to a decoder
                    Log.v(TAG, "Ready to decode image.");
                }

                // Check the type, assuming that it's a Grid image
                if (primaryImage instanceof GridImageItem) {
                    GridImageItem gridImageItem = (GridImageItem) primaryImage;
                    // Go through the grid
                    for (int rowIndex = 0; rowIndex < gridImageItem.getRowCount(); rowIndex++) {
                        for (int columnIndex = 0; columnIndex < gridImageItem.getColumnCount(); columnIndex++) {
                            // We assume that the image items are HEVC
                            HEVCImageItem hevcImageItem = (HEVCImageItem) gridImageItem.getImage(columnIndex, rowIndex);
                            byte[] decoderConfig = hevcImageItem.getDecoderConfig().getConfig();
                            byte[] imageData = hevcImageItem.getItemDataAsArray();
                            // Feed the data to a decoder
                        }
                    }
                }

                // Check the type to find out the depth image.
                for (ImageItem item : heif.getImages()) {
                    boolean hasDepthImage = false;
                    for (ItemProperty prop : item.getProperties()) {
                        Log.d(TAG, item.toString());
                        if (prop instanceof AuxiliaryProperty auxProp) {
                            if (auxProp.getType().equals(AuxiliaryProperty.DEPTH_URN)) {
                                hasDepthImage = true;
                                Log.d(TAG, auxProp.toString());
                                break;
                            }
                        }
                    }

                    if (hasDepthImage && item instanceof HEVCImageItem) {
                        HEVCImageItem hevcImageItem = (HEVCImageItem)item;
                        byte[] decoderConfig = hevcImageItem.getDecoderConfig().getConfig();
                        byte[] imageData = hevcImageItem.getItemDataAsArray();
                        // Feed the data to a decoder

                        // Use MediaCodec to decode data.
                        MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC);
                        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 576, 768);
                        format.setInteger(MediaFormat.KEY_FRAME_RATE, 1);
                        codec.configure(format, null, null, 0);
                        codec.start();
                        while (true) {
                            int inputIndex = codec.dequeueInputBuffer(-1);
                            Log.d(TAG, "dequeueInputBuffer=" + inputIndex);
                            if (inputIndex >= 0) {
                                ByteBuffer tmp = codec.getInputBuffer(inputIndex);
                                tmp.clear();
                                tmp.put(decoderConfig);
                                tmp.put(imageData);
                                tmp.rewind();
                                codec.queueInputBuffer(inputIndex, 0, decoderConfig.lenght + imageData.length, 0, 0);
                            } else {
                                continue;
                            }

                            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                            int outputIndex = codec.dequeueOutputBuffer(info, -1);
                            Log.d(TAG, "dequeueOutputBuffer=" + outputIndex);
                            if (outputIndex >= 0) {
                                codec.releaseOutputBuffer(outputIndex, 0);
                            }
                            break;
                        }
                        codec.stop();
                        codec.release();

                        // Use native code to decode data.
                        ByteBuffer bb = ByteBuffer.allocate(decoderConfig.length + imageData.length);
                        bb.put(decoderConfig);
                        bb.put(imageData);
                        byte[] allData = bb.array();
                        dpt.decode(allData, allData.length);

                        // Use ImageDecoder to decode data
                        ImageDecoder.Source src = ImageDecoder.createSource(bb);
                        Drawable dw = ImageDecoder.decodeDrawable(src);

                        byte[] inputBuffer2 = getContentResolver().openInputStream(selectedImage).readAllBytes();
                        dpt.decode(inputBuffer2, inputBuffer2.length);
                        ImageDecoder.Source src = ImageDecoder.createSource(inputBuffer2);
                        Bitmap bitmap = ImageDecoder.decodeBitmap(src);
                        Log.i(TAG, "bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight());

                        Log.i(TAG, "iamrafael");
                    }
                }
            } catch (Exception e) {
                // All exceptions thrown by the HEIF library are of the same type
                // Check the error code to see what happened
                e.printStackTrace();
                Log.i(TAG, "youarerafael");
            }
        }
    }
}
