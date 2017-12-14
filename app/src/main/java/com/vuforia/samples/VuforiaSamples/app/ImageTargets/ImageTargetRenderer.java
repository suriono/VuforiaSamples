/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.vuforia.samples.VuforiaSamples.app.ImageTargets;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import java.util.StringTokenizer;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.StrictMode;
import android.util.Log;

import com.vuforia.Device;
import com.vuforia.Matrix34F;
import com.vuforia.Matrix44F;
import com.vuforia.State;
import com.vuforia.Tool;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.Vec3F;
import com.vuforia.Vuforia;
import com.vuforia.samples.SampleApplication.SampleAppRenderer;
import com.vuforia.samples.SampleApplication.SampleAppRendererControl;
import com.vuforia.samples.SampleApplication.SampleApplicationSession;
import com.vuforia.samples.SampleApplication.utils.CubeShaders;
import com.vuforia.samples.SampleApplication.utils.LoadingDialogHandler;
import com.vuforia.samples.SampleApplication.utils.SampleApplication3DModel;
import com.vuforia.samples.SampleApplication.utils.SampleMath;
import com.vuforia.samples.SampleApplication.utils.SampleUtils;
import com.vuforia.samples.SampleApplication.utils.Teapot;
import com.vuforia.samples.SampleApplication.utils.Texture;


// The renderer class for the ImageTargets sample. 
public class ImageTargetRenderer implements GLSurfaceView.Renderer, SampleAppRendererControl
{
    private static final String LOGTAG = "ImageTargetRenderer";
    
    private SampleApplicationSession vuforiaAppSession;
    private ImageTargets mActivity;
    private SampleAppRenderer mSampleAppRenderer;

    private Vector<Texture> mTextures;
    
    private int shaderProgramID;
    private int vertexHandle;
    private int textureCoordHandle;
    private int mvpMatrixHandle;
    private int texSampler2DHandle;
    
    private Teapot mTeapot;
    
    private float kBuildingScale = 0.012f;
    private SampleApplication3DModel mBuildingsModel;

    private boolean mIsActive = false;
    private boolean mModelIsLoaded = false;
    
    private static final float OBJECT_SCALE_FLOAT = 0.003f;

    private long lasttime = 0;

    // Uz below
    private WifiManager wifimanager;                     // Uz
    private WifiInfo wifi_info;                           // Uz
    //public static String ipAddress ="192.168.254.28";// laptop ip
    public static String ipAddress ="192.168.4.2";// laptop ip with robot AP
    //public static String ipAddress ="192.168.254.29";   // robot IP
    //public static String ipAddress ="192.168.4.1";   // robot AP IP
    public static int portNumber = 8000;// portnumber
    private Socket socket_client;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private InetSocketAddress socket_address;

    
    
    public ImageTargetRenderer(ImageTargets activity, SampleApplicationSession session)
    {
        mActivity = activity;
        vuforiaAppSession = session;
        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mSampleAppRenderer = new SampleAppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 0.01f , 5f);

        //Log.d(LOGTAG, "===================ImageTargetRenderer");

        // Uz
        //wifimanager = (WifiManager) mActivity.getSystemService(Context.WIFI_SERVICE); // Uz
        //wifi_info = wifimanager.getConnectionInfo();
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        socket_address = new InetSocketAddress(ipAddress, portNumber);
        if (openTCPIP()) {
            sendTCPIP("TCIP is open");
        }
    }

    // Uz to open TCP IP data
    private boolean openTCPIP() {
        boolean if_success = false;
        try {

            socket_client = new Socket();
            socket_client.connect(socket_address, 1000);
            //socket_client.connect(new InetSocketAddress(ipAddress, portNumber), 1000);
            if(socket_client.isConnected()) {
                dataOutputStream = new DataOutputStream(socket_client.getOutputStream());
                dataInputStream = new DataInputStream(socket_client.getInputStream());
                return true;

            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return if_success;
    }

    // Uz send TCP IP data
    private void sendTCPIP(String outstr) {
        try {
            dataOutputStream.writeUTF(outstr);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        finally{
            if (socket_client != null){
                try {
                    socket_client.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (dataOutputStream != null){
                try {
                    dataOutputStream.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            if (dataInputStream != null){
                try {
                    dataInputStream.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    } // End of sendTCPIP
    
    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl)
    {
        if (!mIsActive)
            return;

        //Log.d(LOGTAG, " ====UZ   onDrawFrame =============");
        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render();
    }
    

    public void setActive(boolean active)
    {
        mIsActive = active;

        if(mIsActive)
            mSampleAppRenderer.configureVideoBackground();
    }


    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config)
    {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");
        
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();

        mSampleAppRenderer.onSurfaceCreated();
    }
    
    
    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");
        
        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);

        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive);

        initRendering();
    }
    
    
    // Function for initializing the renderer.
    private void initRendering()
    {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);
        
        for (Texture t : mTextures)
        {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, t.mData);
        }
        
        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
            CubeShaders.CUBE_MESH_VERTEX_SHADER,
            CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexPosition");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
            "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
            "texSampler2D");

        if(!mModelIsLoaded) {
            mTeapot = new Teapot();

            try {
                mBuildingsModel = new SampleApplication3DModel();
                mBuildingsModel.loadModel(mActivity.getResources().getAssets(),
                        "ImageTargets/Buildings.txt");
                mModelIsLoaded = true;
            } catch (IOException e) {
                Log.e(LOGTAG, "Unable to load buildings");
            }

            // Hide the Loading Dialog
            mActivity.loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }

    }

    public void updateConfiguration()
    {
        mSampleAppRenderer.onConfigurationChanged(mIsActive);
    }

    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    public void renderFrame(State state, float[] projectionMatrix)
    {

        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // handle face culling, we need to detect if we are using reflection
            // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);


        // Did we find any trackables this frame?
        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
            TrackableResult result = state.getTrackableResult(tIdx);
            Trackable trackable = result.getTrackable();

            // Below by Uz
            //Matrix34F pose = result.getPose();
            //float[] posedata = pose.getData();
            //float[] posedataarr = {posedata[3], posedata[7], posedata[11]};
            //Vec3F positi = new Vec3F(posedataarr);
            //float[] positidata = positi.getData();
            //String distance = String.format("%.02f", Math.sqrt( positidata[0] * positidata[0] +
            //        positidata[1] * positidata[1] +
            //       positidata[2] * positidata[2]));
            // one way to obtain location but other method below them are more accurate
            //String Xstr = String.format("%.02f", positidata[0]*100);
            //String Ystr = String.format("%.02f", positidata[1]*100);
            //String Zstr = String.format("%.02f", positidata[2]*100);
            //Log.d(LOGTAG, "=====UZ data   " + distance + " , "  + Xstr + " , " + Ystr + " , " +  Zstr );
            // End by Uz

            //printUserData(trackable);

                Matrix44F modelViewMatrix_Vuforia = Tool
                        .convertPose2GLMatrix(result.getPose());

                // by Uz

                Matrix44F inverseMV = SampleMath.Matrix44FInverse(modelViewMatrix_Vuforia); // Uz
                Matrix44F invTranspMV = SampleMath.Matrix44FTranspose(inverseMV);           // Uz
                double cam_x = invTranspMV.getData()[12] * 100.0;
                double cam_y = invTranspMV.getData()[13] * 100.0;
                double cam_z = invTranspMV.getData()[14] * 100.0;

                // phone offset in the robot:
                cam_x = cam_x - 0.0;

                String cam_x_str = String.format("%.02f", cam_x);
                String cam_y_str = String.format("%.02f", cam_y);
                String cam_z_str = String.format("%.02f", cam_z);
                //double distance = Math.sqrt( cam_x*cam_x + cam_y*cam_y + cam_z*cam_z);  // total distance
                double distance = Math.sqrt(cam_x * cam_x + cam_z * cam_z);  // lateral distance
                String distance_str = String.format("%.02f", distance);

                // camera viewing direction, camera right direction, and camera up direction):
                float cam_right_x = invTranspMV.getData()[0];
                float cam_right_y = invTranspMV.getData()[1];
                float cam_right_z = invTranspMV.getData()[2];
                float cam_up_x = -invTranspMV.getData()[4];
                float cam_up_y = -invTranspMV.getData()[5];
                float cam_up_z = -invTranspMV.getData()[6];
                float cam_dir_x = invTranspMV.getData()[8] * 100.0f;
                float cam_dir_y = invTranspMV.getData()[9] * 100.0f;
                float cam_dir_z = invTranspMV.getData()[10] * 100.0f;

                double cam_angle = Math.atan2(cam_dir_x, -cam_dir_z) * 180.0 / Math.PI;
                double cam_pict_center_angle = cam_angle + Math.atan2(cam_x, cam_z) * 180.0 / Math.PI;

                String cam_dir_x_str = String.format("%.02f", cam_dir_x);
                String cam_dir_y_str = String.format("%.02f", cam_dir_y);
                String cam_dir_z_str = String.format("%.02f", cam_dir_z);
                String cam_angle_str = String.format("%.01f", cam_angle);
                String cam_pict_center_angle_str = String.format("%.01f", cam_pict_center_angle);


                if ((System.currentTimeMillis() - lasttime) > 300) {

                    //Log.d(LOGTAG, "=============== SSID = " +  wifi_info.getSSID());

                    if (openTCPIP()) {

                        Log.d(LOGTAG, "=====UZ camera(angle,distance, angle_center,x,y,z):" + cam_angle_str + " , " + distance_str + " , " + cam_pict_center_angle_str + " , " + cam_x_str + " , " + cam_y_str + " , " + cam_z_str);

                        //sendTCPIP("angle=" + cam_angle_str + ", dist= " + distance_str + ", angle to center=" + cam_pict_center_angle_str + ",xyz=" + cam_x_str + " , " + cam_y_str + " , " + cam_z_str);
                        sendTCPIP("cam_angle=" + cam_angle_str + "xyz=" + cam_x_str + "," + cam_y_str + "," + cam_z_str + "<");


                        // The following for getting the robot to follow a picture
                        if (distance > 75.0 || (distance > 30.0 && Math.abs(cam_pict_center_angle) > 10.0)) {

                            int angle_to_turn = (int) cam_pict_center_angle;  // positive = need to turn left
                            //int motorpower = 60;   // 0-127 for power
                            int motorpower = Math.min((int) (distance) / 2, 100);

                            if (distance <= 75.0) {
                                angle_to_turn = (int) Math.signum(cam_pict_center_angle) * 90;
                                motorpower = 20;
                            } else {   // when too much angle to cause oscillation
                                angle_to_turn = Math.min(angle_to_turn, 30);
                            }

                            // Conversion to a robot which take speed and angle. Angle start from polar 0 degree
                            // first 7 bits are power, bit 8-14 next 7 bit for angle. Angle is divide by 3 thus 0-120
                            int angle_power = ((int) ((90 + angle_to_turn) / 3) << 7) + motorpower; // angle with 3 degree increment
                            String anglespeed_str = String.format("%0$d", angle_power);
                            //sendTCPIP("anglespeed=" + anglespeed_str);

                        } else {
                            //sendTCPIP("stop");
                        }
                        //wait(1000);
                        //try {
                        //    Thread.sleep(100);
                        //} catch (InterruptedException e) {
                            //Handle exception
                        //}
                    }
                    lasttime = System.currentTimeMillis();
                }


                float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

                int textureIndex = trackable.getName().equalsIgnoreCase("stones") ? 0
                        : 1;
                textureIndex = trackable.getName().equalsIgnoreCase("tarmac") ? 2
                        : textureIndex;


                // deal with the modelview and projection matrices
                float[] modelViewProjection = new float[16];

                if (!mActivity.isExtendedTrackingActive()) {
                    Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f,
                            OBJECT_SCALE_FLOAT);
                    Matrix.scaleM(modelViewMatrix, 0, OBJECT_SCALE_FLOAT,
                            OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT);

                    //Matrix.rotateM(modelViewMatrix, 0, 0, 0.0f, 90.0f, 0);

                } else {
                    Matrix.rotateM(modelViewMatrix, 0, 90.0f, 1.0f, 0, 0);
                    Matrix.scaleM(modelViewMatrix, 0, kBuildingScale,
                            kBuildingScale, kBuildingScale);
                }
                Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0);

                // activate the shader program and bind the vertex/normal/tex coords
                GLES20.glUseProgram(shaderProgramID);

                if (!mActivity.isExtendedTrackingActive()) {
                    GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                            false, 0, mTeapot.getVertices());
                    GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                            GLES20.GL_FLOAT, false, 0, mTeapot.getTexCoords());

                    GLES20.glEnableVertexAttribArray(vertexHandle);
                    GLES20.glEnableVertexAttribArray(textureCoordHandle);

                    // activate texture 0, bind it, and pass to shader
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                            mTextures.get(textureIndex).mTextureID[0]);
                    GLES20.glUniform1i(texSampler2DHandle, 0);

                    // pass the model view matrix to the shader
                    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                            modelViewProjection, 0);

                    // finally draw the teapot
                    GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                            mTeapot.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
                            mTeapot.getIndices());

                    // disable the enabled arrays
                    GLES20.glDisableVertexAttribArray(vertexHandle);
                    GLES20.glDisableVertexAttribArray(textureCoordHandle);
                } else {
                    GLES20.glDisable(GLES20.GL_CULL_FACE);
                    GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                            false, 0, mBuildingsModel.getVertices());
                    GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                            GLES20.GL_FLOAT, false, 0, mBuildingsModel.getTexCoords());

                    GLES20.glEnableVertexAttribArray(vertexHandle);
                    GLES20.glEnableVertexAttribArray(textureCoordHandle);

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                            mTextures.get(3).mTextureID[0]);
                    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                            modelViewProjection, 0);
                    GLES20.glUniform1i(texSampler2DHandle, 0);
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0,
                            mBuildingsModel.getNumObjectVertex());

                    SampleUtils.checkGLError("Renderer DrawBuildings");
                }

                SampleUtils.checkGLError("Render Frame");

            }

            GLES20.glDisable(GLES20.GL_DEPTH_TEST);

    }

    private void printUserData(Trackable trackable)
    {
        String userData = (String) trackable.getUserData();
        Log.d(LOGTAG, "UserData:Retreived User Data ImageTargetRenderer	\"" + userData + "\"");
    }
    
    
    public void setTextures(Vector<Texture> textures)
    {
        mTextures = textures;
        
    }
    
}
