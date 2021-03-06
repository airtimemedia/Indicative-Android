package com.indicative.client.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

/**
 * Standalone client for Indicative's REST API.  Events are stored in SharedPreferences, 
 * then periodically sent to us in a background thread (SendEventsTimerThread).  
 */

public class Indicative {

	private static Indicative instance;
	
	// Enable this to see some basic logging.
	private static final boolean debug = false;
	
	// How long to wait before sending each batch of events. 
	private static final int SEND_EVENTS_TIMER_SECONDS = 60;

    private static final String EVENT_PREFS = "indicative_events";
    private static final String UNIQUE_PREFS = "indicative_unique";
    private static final String PROPS_PREFS = "indicative_prop_cache";

	private Context context;
	private String apiKey;

    private SharedPreferences eventPrefs;
    private SharedPreferences uniquePrefs;
    private SharedPreferences propsPrefs;

	private Indicative() {
	}

	/**
	 * Instantiates the static Indicative instance and returns it.
	 * 
	 * @return 	The static Indicative instance
	 */
	public static Indicative getInstance() {
		if (instance == null) {
			instance = new Indicative();
		}
		return instance;
	}

    /**
     * Initializes the static Indicative instance with the project's API Key.
     *
     * @param context	The app context
     * @param apiKey	Your project's API Key
     *
     * @return 	The static Indicative instance
     */
    public static Indicative launch(Context context, String apiKey) {
        Indicative instance = getInstance();
        instance.apiKey = apiKey;
        instance.context = context;

        instance.eventPrefs = context.getSharedPreferences(
                EVENT_PREFS, Context.MODE_PRIVATE);

        instance.uniquePrefs = context.getSharedPreferences(
                UNIQUE_PREFS, Context.MODE_PRIVATE);

        instance.propsPrefs = context.getSharedPreferences(
                PROPS_PREFS, Context.MODE_PRIVATE);

        setUUIDInUniquePrefs();

        instance.scheduleEventsTimer();

        return instance;
    }

	/**
	 * Schedules the timer to periodically send events (once every 60 seconds by default)
	 */
	public void scheduleEventsTimer() {
		Handler handler = new Handler();
		handler.post(new SendEventsTimerThread(context, handler));
	}

    /**
     * Sends specific event now (doesn't queue and wait for thread)
     */
    private void sendEventNow(String event) {
        if (event != null && !event.isEmpty()) {
            new SendEventAsyncTask(getInstance().context, event).execute();
        }
    }

    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     * @param uniqueId		A unique identifier for the user associated with the event
     * @param properties	A Map of property names and values
     * @param forceUpload   A flag when set to true, doesn't queue the event but pushes it right away
     */
    public static void recordEvent(String eventName, String uniqueId, Map<String, Object> properties, boolean forceUpload) {

        Map<String, Object> propMap = getAllPropertiesFromSharedPrefs();
        if (properties != null) { propMap.putAll(properties); }

        if (uniqueId == null || uniqueId.isEmpty()) {
            uniqueId = getUniqueIDFromSharedPrefs();
        }

        Event event = new Event(getInstance().apiKey, eventName, uniqueId,
                propMap);
        String jsonObj = event.getEventAsJSON().toString();

        if (forceUpload) {
            getInstance().sendEventNow(jsonObj);
        } else {
            addEventToSharedPrefs(jsonObj);

        }

        if (debug) {
            Log.v("Indicative",
                    new StringBuilder("Recorded event: ").append(jsonObj)
                            .toString());
        }
    }

    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     * @param uniqueId		A unique identifier for the user associated with the event
     * @param properties	A Map of property names and values
     */
    public static void recordEvent(String eventName, String uniqueId, Map<String, Object> properties) {
        recordEvent(eventName, uniqueId, properties, false);
    }


    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     */
    public static void recordEvent(String eventName) {
    	recordEvent(eventName, null, null);
    }

    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     * @param forceUpload   A flag when set to true, doesn't queue the event but pushes it right away
     */
    public static void recordEvent(String eventName, boolean forceUpload) {
        recordEvent(eventName, null, null, forceUpload);
    }

    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     * @param uniqueId		A unique identifier for the user associated with the event
     */
    public static void recordEvent(String eventName, String uniqueId) {
    	recordEvent(eventName, uniqueId, null);
    }

    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     * @param uniqueId		A unique identifier for the user associated with the event
     * @param forceUpload   A flag when set to true, doesn't queue the event but pushes it right away
     */
    public static void recordEvent(String eventName, String uniqueId, boolean forceUpload) {
        recordEvent(eventName, uniqueId, null, forceUpload);
    }

    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     * @param properties	A Map of property names and values
     */
    public static void recordEvent(String eventName, Map<String, Object> properties) {
    	recordEvent(eventName, null, properties);
    }

    /**
     * Creates an Event object and adds it to SharedPreferences queue to send to Indicative input services.
     *
     * @param eventName		The name of your event
     * @param properties	A Map of property names and values
     * @param forceUpload   A flag when set to true, doesn't queue the event but pushes it right away
     */
    public static void recordEvent(String eventName, Map<String, Object> properties, boolean forceUpload) {
        recordEvent(eventName, null, properties, forceUpload);
    }

    /**
     * Sets a unique ID to be used on all following events that does not explicitly set one
     *
     * @param uniqueID  A unique identifier for the user associated with all events
     */
    public static void setUniqueID(String uniqueID) {
        setUniqueIDToSharedPrefs(uniqueID);
    }

    /**
     * Clears the unique ID that is used on all events
     */
    public static void clearUniqueID() {
        clearUniqueIDInSharedPrefs();
    }

    /**
     * Adds a property to the common property cached list in SharedPreferences
     *
     * @param name      The property's unique name
     * @param value     The property's value based on user or event
     */
    public static void addProperty(String name, String value) {
        addPropertyToSharedPrefs(name, value);
    }

    /**
     * Adds a property to the common property cached list in SharedPreferences
     *
     * @param name      The property's unique name
     * @param value     The property's value based on user or event
     */
    public static void addProperty(String name, int value) {
        addPropertyToSharedPrefs(name, value);
    }

    /**
     * Adds a property to the common property cached list in SharedPreferences
     *
     * @param name      The property's unique name
     * @param value     The property's value based on user or event
     */
    public static void addProperty(String name, boolean value) {
        addPropertyToSharedPrefs(name, value);
    }

    /**
     * Adds a map of common properties to the cached list in SharedPreferences
     *
     * @param properties
     */
    public static void addProperties(Map<String, Object> properties) {
        for(Map.Entry<String, Object> prop : properties.entrySet()) {
            if (prop.getValue() instanceof Boolean) {
                addProperty(prop.getKey(), (Boolean) prop.getValue());
            } else if (prop.getValue() instanceof String) {
                addProperty(prop.getKey(), (String) prop.getValue());
            } else if (prop.getValue() instanceof Integer) {
                addProperty(prop.getKey(), (Integer) prop.getValue());
            }

        }
    }

    /**
     * Removes a single property from the cached list of common
     * properties in SharedPreferences
     *
     * @param name      The property's unique name or key to remove
     */
    public static void removeProperty(String name) {
        removePropertyFromSharedPrefs(name);
    }

    /**
     * Clears the entire list of shared common properties in SharedPreferences
     */
    public static void clearProperties() {
        removePropertiesFromSharedPrefs();
    }
	
	/**
	 * Adds the Event object to SharedPreferences
	 * 
	 * @param jsonObj		A JSON representation of the event
	 */
	private static synchronized void addEventToSharedPrefs(String jsonObj){
		if(getInstance().context == null){
			Log.v("Indicative", "Indicative instance has not been initialized; not recording event");
			return;
		}
		SharedPreferences prefs = getInstance().eventPrefs;
		int eventCount = prefs.getInt(jsonObj, 0);
		prefs.edit().putInt(jsonObj, eventCount + 1).apply();
	}

    private static synchronized void setUUIDInUniquePrefs(){
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not setting up unique id");
            return;
        }

        SharedPreferences prefs = getInstance().uniquePrefs;
        String uuid = prefs.getString("uuid", null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString("uuid", uuid).apply();
        }
    }

    /**
     *  Adds the unique id to SharedPreferences
     *
     * @param unique    A string of a unique identifier for this user
     */
    private static synchronized void setUniqueIDToSharedPrefs(String unique) {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not setting up unique id");
            return;
        }

        SharedPreferences prefs = getInstance().uniquePrefs;
        prefs.edit().putString(UNIQUE_PREFS, unique).apply();
    }

    /**
     *  Gets the unique identifier from SharedPreferences
     */
    private static synchronized String getUniqueIDFromSharedPrefs() {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not setting up unique id");
            return null;
        }

        SharedPreferences prefs = getInstance().uniquePrefs;
        String uniqueID = prefs.getString(UNIQUE_PREFS, null);

        if (uniqueID == null || uniqueID.isEmpty()) {
            uniqueID = prefs.getString("uuid", null);
        }

        return uniqueID;
    }

    /**
     *  Clears the unique id cached in SharedPreferences
     */
    private static synchronized void clearUniqueIDInSharedPrefs() {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not clearing unique id");
            return;
        }

        SharedPreferences prefs = getInstance().uniquePrefs;
        prefs.edit().remove(UNIQUE_PREFS).apply();
    }

    /**
     * Adds a common property to the SharedPreferences
     *
     * @param key		A property's key or name
     * @param val       A property's value
     */
    private static synchronized void addPropertyToSharedPrefs(String key, String val) {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not adding common prop");
            return;
        }

        SharedPreferences prefs = getInstance().propsPrefs;
        prefs.edit().putString(key, val).apply();
    }

    /**
     * Adds a common property to the SharedPreferences
     *
     * @param key		A property's key or name
     * @param val       A property's value
     */
    private static synchronized void addPropertyToSharedPrefs(String key, int val) {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not adding common prop");
            return;
        }

        SharedPreferences prefs = getInstance().propsPrefs;
        prefs.edit().putInt(key, val).apply();
    }

    /**
     * Adds a common property to the SharedPreferences
     *
     * @param key		A property's key or name
     * @param val       A property's value
     */
    private static synchronized void addPropertyToSharedPrefs(String key, boolean val) {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not adding common prop");
            return;
        }

        SharedPreferences prefs = getInstance().propsPrefs;
        prefs.edit().putBoolean(key, val).apply();
    }

    /**
     * Removes a single property from the property cache in SharedPreferences
     *
     * @param key   The property's key to be removed
     */
    private static synchronized void removePropertyFromSharedPrefs(String key) {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not adding common prop");
            return;
        }

        SharedPreferences prefs = getInstance().propsPrefs;
        prefs.edit().remove(key).apply();
    }

    /**
     * Clear all of the cached properties in the SharedPreferences
     */
    private static synchronized void removePropertiesFromSharedPrefs() {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not adding common prop");
            return;
        }

        SharedPreferences prefs = getInstance().propsPrefs;
        prefs.edit().clear().apply();
    }

    /**
     * Gets all the cached properties in the SharedPreferences
     */
    private static synchronized Map<String, Object> getAllPropertiesFromSharedPrefs() {
        if (getInstance().context == null) {
            Log.v("Indicative", "Indicative instance has not been initialized; not getting common props");
            return new HashMap<String, Object>();
        }

        SharedPreferences prefs = getInstance().propsPrefs;
        return (Map<String,Object>)prefs.getAll();
    }


    /**
	 * Object representing an Indicative Event
	 */
	public static class Event {
		
		private String apiKey;
		private String eventName;
		private long eventTime;
		private String eventUniqueId;
		private Map<String, Object> properties;
		
		/**
		 * Basic constructor.
		 * 
		 * @param apiKey			Your project's API key
		 * @param eventName			The name of your event
		 * @param eventUniqueId		A unique identifier for the user associated with the event
		 * @param properties		A Map of property names and values
		 */
		public Event(String apiKey, String eventName, String eventUniqueId, Map<String, Object> properties){
			this.apiKey = apiKey;
			this.eventName = eventName;
			this.eventTime = System.currentTimeMillis();
			this.eventUniqueId = eventUniqueId;
			this.properties = properties;
		}
		
		/**
		 * Creates a JSON representation of the Event.
		 */
		public JSONObject getEventAsJSON(){
			JSONObject event = new JSONObject();
			
			try {
				event.put("apiKey", apiKey);
				event.put("eventName", eventName);
				event.put("eventTime", eventTime);
				event.put("eventUniqueId", eventUniqueId);
				if(properties != null && !properties.isEmpty()){
					JSONObject propsJson = new JSONObject();
					for(Entry<String, Object> entry : properties.entrySet()){
						propsJson.put(entry.getKey(), entry.getValue());
					}
					event.put("properties", propsJson);
				}
			} catch (JSONException e) {
				Log.v("Indicative", "Event" + e.getMessage(), e.fillInStackTrace());
			}
			
			return event;
		}
	}

    /**
     *  Clear the cached events by sending them all now using the ASYNC task.
     */
    public static void sendAllEvents() {
        getInstance().sendAllEvents(getInstance().context);
    }


    /**
     *  Clear the cached events by sending them all now using the ASYNC task.
     *
     *  @param context  instance context
     */
    public synchronized void sendAllEvents(Context context) {
        SharedPreferences prefs = eventPrefs;
        Map<String, ?> events = prefs.getAll();

        if(events != null && !events.isEmpty()){
            for (String event : events.keySet()) {
                for(int i = 0 ; i < (Integer)events.get(event) ; i++) {
                    new SendEventAsyncTask(context, event).execute();

                    //remove from preferences
                    int eventCount = prefs.getInt(event, 0);
                    if (eventCount > 1) {
                        prefs.edit().putInt(event, eventCount - 1).apply();
                    } else {
                        prefs.edit().remove(event).apply();
                    }
                }
            }
        }
    }

	
	/**
	 * Scheduled timer to periodically send Events to the Indicative API endpoint.
	 */
	public class SendEventsTimerThread extends Thread {
		private Context context;
		private Handler handler;
		
		SendEventsTimerThread(Context context, Handler handler) {
			this.context = context;
			this.handler = handler;
			setName("SendEventsTimer");
		}
		
		/**
		 * For each Event in SharedPreferences, this executes an AsyncTask to send it to the Indicative API endpoint.
		 */
		@Override
		public void run() {
			
			if(debug){
				Log.v("Indicative", "Timer: Running send events timer");
			}

            sendAllEvents(context);
            handler.postDelayed(this, SEND_EVENTS_TIMER_SECONDS * 1000l);
        }
	}
	
	/**
	 * Async Task to send Events to the Indicative API endpoint.
	 */

	public class SendEventAsyncTask extends AsyncTask<Void, Void, Integer> {

		private static final String API_ENDPOINT = "https://api.indicative.com/service/event";

		private Context context;
		private String event;

		public SendEventAsyncTask(Context context, String event) {
			this.context = context;
			this.event = event;
		}
		
		/**
		 * Initializes the static Indicative instance with the project's API Key.
		 * 
		 * @param params	Not used
		 * 
		 * @return 			The status code returned by Indicative
		 */
		@Override
		protected Integer doInBackground(Void... params) {
			if (debug) {
				Log.v("Indicative", new StringBuilder("Async Task: Sending event: ")
						.append(event).toString());
			}

			int statusCode = 0;

			try {
				HttpParams httpParameters = new BasicHttpParams();
				HttpConnectionParams.setSocketBufferSize(httpParameters, 8192);

				HttpClient client = new DefaultHttpClient(httpParameters);
				HttpPost post = new HttpPost(API_ENDPOINT);

				post.setHeader("Content-Type", "application/json");
				post.addHeader("Indicative-Client", "Android");

				post.setEntity(new StringEntity(event, "UTF-8"));

				HttpResponse resp = client.execute(post);

				statusCode = resp.getStatusLine().getStatusCode();

				if (debug) {
					Log.v("Indicative", new StringBuilder("Status Code: ").append(Integer.toString(statusCode)).toString());
					Log.v("Indicative", new StringBuilder("Status Reason: ").append(resp.getStatusLine().getReasonPhrase()).toString());
					Log.d("Indicative", new StringBuilder("Response Body: ").append(inputStreamToString(resp.getEntity().getContent())).toString());
				}

				return statusCode;
			} catch (Exception e) {
				Log.v("Indicative", new StringBuilder("AsyncTask: ").append(e.getMessage()).toString(), e);
			}

			return 400;
		}

		/**
		 * Removes the Event from SharedPreferences if it was posted successfully, 
		 * or if it received a response indicating a non-retriable error.
		 */
		@Override
		protected void onPostExecute(Integer result) {

            if (result != 0 && result != 408 && result != 500) {
                //do nothing, already removed in sendAllEvents
                if (debug) {
                    Log.v("Indicative", new StringBuilder("Async Task: event successful ").append(event).toString());
                }
			} else {
                //add it back into shared prefs if that's the case
                addEventToSharedPrefs(event);
                if (debug) {
                    Log.v("Indicative", " Async Task: Retriable error occured");
                }
            }
		}

		/**
		 * Converts the response's input stream to a String for readability.
		 * 
		 * @param is	The InputStream
		 * 
		 * @return 		A String representation of the InputStream
		 */
		private String inputStreamToString(InputStream is) {
			String line = "";
			StringBuilder total = new StringBuilder();

			BufferedReader rd = new BufferedReader(new InputStreamReader(is));

			try {
				while ((line = rd.readLine()) != null) {
					total.append(line);
				}
			} catch (IOException e) {
				Log.v("Indicative", new StringBuilder(" Async Task: ").append(e.getMessage()).toString(), e);
			}

			return total.toString();
		}
	}
}
