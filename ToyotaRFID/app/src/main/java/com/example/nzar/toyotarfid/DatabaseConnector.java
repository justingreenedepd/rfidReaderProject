//Written by Skyelar Craver and Connor Brennan
//OXYS Corp
//2017
package com.example.nzar.toyotarfid;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.JsonReader;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Random;

/**
 * Created by cravers on 6/29/2017.
 */

/*
The Database Connector class is where all interactions with the designated database for this project will happen
This class grabs the data for the equipment being used, the person who is trying to badge in, the ppe requirements
and is also responsible for sending the appropriate data back to the database for keeping logs
*/
class DatabaseConnector extends AppCompatActivity {

    public static ArrayList<PPE> PPEList = new ArrayList<>();
    public static ArrayList<LabTech> LabTechList = new ArrayList<>();
    public static int currentSessionID;
    public static String machineID;
    public static String baseServerUrl;
    public static String currentBadgeID = "";

    static class LabTech {
        int LabTechID;
        String firstName;
        String lastName;
        String email;
        String phoneNumber;
        Drawable Image;
    }

    static class PPE {
        int PPEID;
        String name;
        Drawable Image;
        boolean Required;
        boolean Restricted;
    }

    public static boolean BindPreferences(SharedPreferences prefs) {
        machineID = prefs.getString("machineID", null);
        baseServerUrl = prefs.getString("baseServerUrl", null);

        return (machineID == null || baseServerUrl == null);
    }


    @Nullable
    private static JsonReader TILTAPITask(HttpURLConnection connection, String method) throws Exception {
        final String TILT_API_KEY = "basic VElMVFdlYkFQSToxM1RJTFRXZWJBUEkxMw==";
        Log.d("TILTAPI", "Using key: " + TILT_API_KEY);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Authorization", TILT_API_KEY);
        int ResponseCode = connection.getResponseCode();
        Log.d("TILTAPI", "Respose code: " + String.valueOf(ResponseCode));

        if (ResponseCode == 201 || ResponseCode == 200) {
            InputStream RawResponse = connection.getInputStream();
            InputStreamReader Response = new InputStreamReader(RawResponse, "UTF-8");
            return new JsonReader(Response);


        } else if (connection.getResponseCode() == 400) {
            connection.disconnect();
            return null;
        } else {
            connection.disconnect();
            throw new Exception("bad http response ");
        }
    }

    private static Drawable ImageParser(String jsonImage) throws UnsupportedEncodingException {
        byte encodedImage[] = jsonImage.getBytes();
        Bitmap bitmap = BitmapFactory.decodeByteArray(encodedImage, 15, encodedImage.length);
        return new BitmapDrawable(Resources.getSystem(), bitmap);
    }

    //give the badge number as a string, provide progress messages as Strings, and return a Boolean if the user is allowed
    static class TILTPostUserTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... params) {

            String badgeID = params[0];
            String isLoggingOut, sessionID;
            if (params.length == 2) {
                sessionID = params[1];
                isLoggingOut = "true";
            } else if (params.length == 1) {
                currentSessionID = new Random().nextInt();
                sessionID = String.valueOf(currentSessionID);
                currentBadgeID = badgeID;
                isLoggingOut = "false";
            } else {
                return null;
            }

            final String APIConnectionUrl = "http://" +
                    baseServerUrl +
                    "/TILTWebApi/api/Users" +
                    "?sessionID=" + sessionID +
                    "&machineIP=" + machineID +
                    "&badgeID=" + badgeID +
                    "&isLoggingOut=" + isLoggingOut;

            Log.d("TILTPostUser", APIConnectionUrl);

            try {

                URL url = new URL(APIConnectionUrl);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();


                JsonReader Response = TILTAPITask(connection, "POST");

                if (Response == null) {
                    Log.d("TILTJSON", "JSON response was null");
                }
                assert Response != null;
                boolean UserHasCerts = false, UserIsTech = false, MachineNeedsTech = false;
                Response.beginObject();
                while (Response.hasNext()) {
                    switch (Response.nextName()) {
                        case "MachinePPE":
                            Response = PPEJsonParse(Response);
                            break;
                        case "UserHasCerts":
                            UserHasCerts = Response.nextBoolean();
                            break;
                        case "UserIsTech":
                            UserIsTech = Response.nextBoolean();
                            break;
                        case "MachineNeedsTech":
                            MachineNeedsTech = Response.nextBoolean();
                            break;
                    }
                }
                Response.close();
                connection.disconnect();

                if (UserHasCerts && MachineNeedsTech) {
                    return "RequiresTech";
                } else if (UserIsTech) {
                    return "UserIsTech";
                } else if (UserHasCerts) {
                    return "UserIsAllowed";
                } else {
                    return "UserIsDenied";
                }


            } catch (Exception e) {
                e.printStackTrace();
                return "Exception";
            }


        }
    }

    static class TILTPostTechTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {

            String sessionID;

            if (params.length >= 1) {
                sessionID = params[0];
            } else {
                sessionID = String.valueOf(new Random().nextInt());
            }

            final String content = "testContent";//content of the email message
            final String APIConnectionUrl = "http://" +
                    baseServerUrl +
                    "/TILTWebApi/api/technicians" +
                    "?sessionID=" + sessionID +
                    "&machineIP=" + machineID +
                    "&content=" + content;
            Log.d("TILTPostUser", APIConnectionUrl);


            try {
                URL url = new URL(APIConnectionUrl);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                TILTAPITask(connection, "POST");

                connection.disconnect();

                return true;

            } catch (Exception e) {
                Log.d("POSTTechnician", "Failed to send Email request");
                e.printStackTrace();
                return false;
            }
        }
    }

    static class TILTGetTechTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {

            final String APIConnectionUrl = "http://" +
                    baseServerUrl +
                    "/TILTWebApi/api/technicians";
            Log.d("TILTPostUser", APIConnectionUrl);


            try {
                URL url = new URL(APIConnectionUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                JsonReader ResponseReader = TILTAPITask(connection, "GET");

                if (ResponseReader == null) {
                    Log.d("TILTJSON", "JSON response was null");
                }

                assert ResponseReader != null;
                LabTechList.clear();
                ResponseReader.beginArray();
                while (ResponseReader.hasNext()) {
                    LabTech temp = new LabTech();

                    ResponseReader.beginObject();
                    while (ResponseReader.hasNext()) {
                        String key = ResponseReader.nextName();
                        switch (key) {
                            case ("LabTechID"):
                                temp.LabTechID = ResponseReader.nextInt();
                                break;
                            case ("FirstName"):
                                temp.firstName = ResponseReader.nextString();
                                break;
                            case ("LastName"):
                                temp.lastName = ResponseReader.nextString();
                                break;
                            case ("Email"):
                                temp.email = ResponseReader.nextString();
                                break;
                            case ("PhoneNumber"):
                                temp.phoneNumber = ResponseReader.nextString();
                                break;
                            case "Photo":
                                temp.Image = ImageParser(ResponseReader.nextString());
                            default:
                                ResponseReader.skipValue();
                                break;
                        }
                    }
                    LabTechList.add(temp);
                    ResponseReader.endObject();

                }
                ResponseReader.endArray();
                ResponseReader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    static JsonReader PPEJsonParse(JsonReader Response) throws IOException {
        Response.beginArray();

        PPEList.clear();
        while (Response.hasNext()) {
            PPE ppe = new PPE();
            Response.beginObject();
            while (Response.hasNext()) {
                //parse response for PPE info
                //if the response is not empty, set UserAuthorized to true
                String key = Response.nextName();
                switch (key) {
                    case "PPEID":
                        ppe.PPEID = Response.nextInt();
                        break;
                    case "PPE":
                        ppe.name = Response.nextString();
                        Log.d("TILTJSON", "Found PPE: " + ppe.name);
                        break;
                    case "Image":
                        ppe.Image = ImageParser(Response.nextString());
                        break;
                    case "Required":
                        ppe.Required = Response.nextBoolean();
                        break;
                    case "Restricted":
                        ppe.Restricted = Response.nextBoolean();
                        break;
                    default:
                        Response.skipValue();
                        break;
                }
            }
            PPEList.add(ppe);
            Response.endObject();
        }
        Response.endArray();
        return Response;
    }


}