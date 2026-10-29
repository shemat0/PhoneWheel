package com.orchestr.phonewheel;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.Manifest;
import android.bluetooth.*;
import android.graphics.*;
import android.hardware.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity implements SensorEventListener {
 private final Handler ui=new Handler(Looper.getMainLooper());
 private final ControlModel model=new ControlModel();
 private final Object signal=new Object();
 private volatile long revision,touchRevision;
 private volatile Connection connection;
 private SensorManager sensors;
 private android.content.SharedPreferences prefs;
 private GameSurface surface;
 private TextView angleLabel,rttLabel,linkLabel,menuStatus;
 private Dialog menu;
 private Spinner devicePicker;
 private EditText codeField;
 private Button connectButton;
 private final ArrayList<BluetoothDevice> paired=new ArrayList<>();
 private double rawAngle,center,lastTarget;
 private boolean resumed,vibration;
 private String feedback="Откройте меню для подключения";
 private final int background=Color.rgb(12,20,32);

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  prefs=getSharedPreferences("wheel",0);vibration=prefs.getBoolean("haptics",true);
  android.content.SharedPreferences.Editor migrated=prefs.edit();
  for(int mode=0;mode<3;mode++){
   boolean old=false;int[] previous=new int[4];
   if(mode<2){for(int i=0;i<4;i++)old|=prefs.contains("slot_"+mode+"_"+i);for(int i=0;i<4;i++){int a=prefs.getInt("slot_"+mode+"_"+i,new int[]{5,6,7,8}[i]);previous[i]=(a==0||a>=5&&a<=8)?a:0;}}
   int[] defaults=old?ControlModel.migrate(mode,previous):null;
   for(int i=0;i<8;i++){
    String key="xbox_slot_"+mode+"_"+i;int action=prefs.getInt(key,defaults==null?model.slot(mode,i):defaults[i]);
    if(action<0||action>18||(mode!=ControlModel.ARROWS&&(action==1||action==2)))action=0;
    model.setSlot(mode,i,action);migrated.putInt(key,action);
   }
  }
  migrated.apply();
  int savedMode=prefs.getInt("controlMode",0);model.setMode(savedMode>=0&&savedMode<=2?savedMode:0);
  sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
  if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(background);
  root.setOnApplyWindowInsetsListener((v,insets)->{
   if(Build.VERSION.SDK_INT>=30){Insets safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.mandatorySystemGestures());v.setPadding(safe.left,safe.top,safe.right,safe.bottom);}
   else {int l=insets.getStableInsetLeft(),t=insets.getStableInsetTop(),r=insets.getStableInsetRight(),b=insets.getStableInsetBottom();if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){DisplayCutout cut=insets.getDisplayCutout();l=Math.max(l,cut.getSafeInsetLeft());t=Math.max(t,cut.getSafeInsetTop());r=Math.max(r,cut.getSafeInsetRight());b=Math.max(b,cut.getSafeInsetBottom());}v.setPadding(l,t,r,b);}
   return insets;
  });
  LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(8),0,dp(8),0);
  Button settings=new Button(this);settings.setText("МЕНЮ");settings.setContentDescription("Настройки и подключение");settings.setOnClickListener(v->openMenu());header.addView(settings,new LinearLayout.LayoutParams(-2,-2));
  angleLabel=label("ГИРО · 0°",18);header.addView(angleLabel,new LinearLayout.LayoutParams(0,-1,1));
  rttLabel=label("RTT —",16);header.addView(rttLabel,new LinearLayout.LayoutParams(0,-1,1));
  linkLabel=label("● Нет связи",15);header.addView(linkLabel,new LinearLayout.LayoutParams(0,-1,1.2f));
  root.addView(header,new LinearLayout.LayoutParams(-1,0,1));
  surface=new GameSurface();root.addView(surface,new LinearLayout.LayoutParams(-1,0,3));
  setContentView(root);root.requestApplyInsets();fullscreen();
  ui.post(ticker);
 }
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 private TextView label(String text,int size){TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(Color.WHITE);view.setGravity(Gravity.CENTER);return view;}
 private void fullscreen(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
 private void changed(){changed(true);}
 private void changed(boolean touch){synchronized(signal){revision++;if(touch)touchRevision++;signal.notifyAll();}if(touch&&surface!=null)surface.invalidate();}
 private void neutral(){model.clear();lastTarget=0;changed();}
 private void recenter(){center=rawAngle;neutral();}
 private final Runnable ticker=new Runnable(){public void run(){
  Connection c=connection;long now=SystemClock.elapsedRealtime();boolean fresh=c!=null&&c.health.fresh(now);
  linkLabel.setText(fresh?"● Связь":"● Нет связи");linkLabel.setTextColor(fresh?Color.rgb(54,213,129):Color.rgb(133,40,47));
  linkLabel.setContentDescription(fresh?"Связь с ПК подтверждена свежей командой":"Нет свежего подтверждения связи с ПК");
  angleLabel.setText((model.mode()==ControlModel.ARROWS?"СТРЕЛКИ · ":model.mode()==ControlModel.STICK?"СТИК · ":"ГИРО · ")+String.format(Locale.ROOT,"%+.0f°",Steering.delta(rawAngle,center)));
  rttLabel.setText(c!=null&&c.health.rtt()>=0?"RTT "+c.health.rtt()+" мс":"RTT —");
  rttLabel.setContentDescription("Время команды туда и обратно. "+rttLabel.getText());
  if(c!=null&&c.active&&((c.health.last()<0&&now-c.started>10000)||(c.health.last()>=0&&now-c.health.last()>1200))){stopConnection();feedback="Нет свежих подтверждений. Подключитесь снова.";}
  if(menu!=null&&menu.isShowing()&&connectButton!=null){if(menuStatus!=null)menuStatus.setText(feedback);connectButton.setText(connection==null?"Подключить":"Отключить");devicePicker.setEnabled(connection==null);codeField.setEnabled(connection==null);}
  ui.postDelayed(this,100);
 }};
 private class GameSurface extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  GameSurface(){super(MainActivity.this);setFocusable(true);setContentDescription("Игровые кнопки. В режиме Стик левая половина — горизонтальный аналоговый руль. Настройка режима и слотов в меню.");}
  private float[] bounds(int cell){float full=getHeight()/.75f;float[] b=model.bounds(cell,getWidth(),full);b[1]-=full*.25f;b[3]-=full*.25f;return b;}
  private int cell(float x,float y){float full=getHeight()/.75f;return model.cellAt(x,y+full*.25f,getWidth(),full);}
  @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);model.clear();changed();}
  private void drawStick(Canvas canvas){
   float[] g=model.stickGeometry(getWidth(),getHeight());float left=getWidth()*.5f;
   paint.setColor(Color.rgb(48,65,92));paint.setStyle(Paint.Style.FILL);canvas.drawCircle(g[0],g[1],g[2],paint);
   paint.setColor(Color.rgb(102,128,157));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawCircle(g[0],g[1],g[2],paint);canvas.drawLine(g[0]-g[2]*.8f,g[1],g[0]+g[2]*.8f,g[1],paint);paint.setStyle(Paint.Style.FILL);
   paint.setColor(Color.rgb(55,168,183));canvas.drawCircle(g[3],g[1],g[2]*.25f,paint);
   paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.min(dp(22),g[2]*.16f));canvas.drawText("−",g[0]-g[2]*.8f,g[1]-dp(10),paint);canvas.drawText("+",g[0]+g[2]*.8f,g[1]-dp(10),paint);
   paint.setTextAlign(Paint.Align.CENTER);paint.setColor(Color.WHITE);paint.setTextSize(Math.min(dp(16),left*.06f));canvas.drawText("Коснитесь и ведите",left/2,getHeight()-dp(32),paint);canvas.drawText("влево / вправо",left/2,getHeight()-dp(12),paint);
  }
  @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.drawColor(background);if(model.mode()==ControlModel.STICK)drawStick(canvas);
   for(int i=0;i<model.cells();i++){
    int action=model.action(i);if(action==0)continue;float[] b=bounds(i);
    int color=action==ControlModel.GAS?Color.rgb(20,122,98):action==ControlModel.BRAKE?Color.rgb(151,48,58):action==ControlModel.NITRO?Color.rgb(159,96,20):Color.rgb(48,65,92);
    paint.setColor(model.held(action)?Color.rgb(91,139,159):color);canvas.drawRoundRect(new RectF(b[0],b[1],b[2],b[3]),dp(12),dp(12),paint);
    String text=ControlModel.label(action);float size=Math.min(dp(28),Math.min((b[2]-b[0])*.22f,(b[3]-b[1])*.25f));paint.setTextSize(size);paint.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
    while(paint.measureText(text)>(b[2]-b[0])*.84f){size*=.94f;paint.setTextSize(size);}
    paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(text,(b[0]+b[2])/2,(b[1]+b[3])/2-(paint.ascent()+paint.descent())/2,paint);
   }
  }
  @Override public boolean onTouchEvent(MotionEvent event){int action=event.getActionMasked(),index=event.getActionIndex();long now=SystemClock.elapsedRealtime();
   if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN){int id=event.getPointerId(index);if(model.mode()==ControlModel.STICK&&event.getX(index)<getWidth()*.5f){if(model.stickDown(id,event.getX(index),event.getY(index),getWidth(),getHeight()))vibrate();}else if(model.press(id,cell(event.getX(index),event.getY(index))))vibrate();}
   else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP){model.stickUp(event.getPointerId(index));model.release(event.getPointerId(index),now);}
   else if(action==MotionEvent.ACTION_MOVE){for(int i=0;i<event.getPointerCount();i++){int id=event.getPointerId(i);if(model.stickOwns(id))model.stickMove(id,event.getX(i),event.getY(i));else model.move(id,cell(event.getX(i),event.getY(i)),now);}}
   else if(action==MotionEvent.ACTION_CANCEL){model.clear();}
   changed();return true;
  }
 }
 private void vibrate(){if(!vibration)return;Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator()){if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(20,VibrationEffect.DEFAULT_AMPLITUDE));else v.vibrate(20);}}
 private void openMenu(){
  if(menu!=null&&menu.isShowing())return;model.block(true);changed();
  menu=new Dialog(this);menu.setTitle("Phone Wheel · настройки");
  ScrollView scroll=new ScrollView(this);LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(16),dp(8),dp(16),dp(8));scroll.addView(panel);
  TextView info=label(feedback,14);menuStatus=info;panel.addView(info);
  panel.addView(label("Режим управления",15));Spinner modes=new Spinner(this);modes.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Гироскоп","Стрелки","Стик"}));modes.setSelection(model.mode());panel.addView(modes);
  LinearLayout assignments=new LinearLayout(this);assignments.setOrientation(1);panel.addView(assignments);
  modes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long id){model.setMode(position);prefs.edit().putInt("controlMode",position).apply();changed();fillSlots(assignments,position);}});
  fillSlots(assignments,model.mode());
  Switch haptic=new Switch(this);haptic.setText("Вибрация");haptic.setChecked(vibration);haptic.setOnCheckedChangeListener((v,on)->{vibration=on;prefs.edit().putBoolean("haptics",on).apply();});panel.addView(haptic);
  Button calibration=new Button(this);calibration.setText("Центр руля");calibration.setOnClickListener(v->{recenter();info.setText("Центр установлен. При выходе из меню центр обновится.");});panel.addView(calibration);
  devicePicker=new Spinner(this);panel.addView(devicePicker);
  Button refresh=new Button(this);refresh.setText("Сопряжённые устройства");refresh.setOnClickListener(v->loadDevices());panel.addView(refresh);
  codeField=new EditText(this);codeField.setSingleLine();codeField.setHint("Код приёмника ПК");codeField.setText(prefs.getString("token",""));panel.addView(codeField);
  connectButton=new Button(this);connectButton.setText(connection==null?"Подключить":"Отключить");connectButton.setOnClickListener(v->{if(connection!=null){stopConnection();info.setText("Отключено");}else{startConnection();info.setText(feedback);}});panel.addView(connectButton);
  Button done=new Button(this);done.setText("ВЕРНУТЬСЯ К ИГРЕ");done.setOnClickListener(v->menu.dismiss());panel.addView(done);
  menu.setContentView(scroll);menu.setOnDismissListener(v->{center=rawAngle;lastTarget=0;model.block(!resumed);changed();menu=null;fullscreen();});menu.show();menu.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.85),-1);loadDevices();
 }
 private void fillSlots(LinearLayout parent,int mode){parent.removeAllViews();
  for(int slot=0;slot<8;slot++){final int index=slot;Button picker=new Button(this);picker.setText("Слот "+(slot+1)+" · "+ControlModel.label(model.slot(mode,slot)));picker.setOnClickListener(v->chooseSlot(parent,mode,index));parent.addView(picker);}
 }
 private void assign(LinearLayout parent,int mode,int slot,int action,Dialog picker){model.setSlot(mode,slot,action);prefs.edit().putInt("xbox_slot_"+mode+"_"+slot,action).apply();changed();fillSlots(parent,mode);picker.dismiss();}
 private void chooseSlot(LinearLayout parent,int mode,int slot){
  model.block(true);changed();Dialog picker=new Dialog(this);picker.setTitle("Слот "+(slot+1)+" · кнопка Xbox");
  LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(12),dp(6),dp(12),dp(6));
  panel.addView(new ControllerDiagram(action->assign(parent,mode,slot,action,picker)),new LinearLayout.LayoutParams(-1,Math.min(dp(270),(int)(getResources().getDisplayMetrics().heightPixels*.55))));
  ArrayList<Integer> actions=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();for(int a=0;a<=18;a++){if(mode!=ControlModel.ARROWS&&(a==1||a==2))continue;actions.add(a);labels.add(ControlModel.label(a));}
  Spinner textChoice=new Spinner(this);textChoice.setContentDescription("Доступный текстовый список всех кнопок Xbox");textChoice.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));int selected=actions.indexOf(model.slot(mode,slot));textChoice.setSelection(Math.max(0,selected));panel.addView(textChoice);
  Button use=new Button(this);use.setText("Назначить выбранную кнопку");use.setOnClickListener(v->assign(parent,mode,slot,actions.get(textChoice.getSelectedItemPosition()),picker));panel.addView(use);
  LinearLayout bottom=new LinearLayout(this);Button empty=new Button(this);empty.setText("Скрыть слот");empty.setOnClickListener(v->assign(parent,mode,slot,0,picker));bottom.addView(empty,new LinearLayout.LayoutParams(0,-2,1));Button cancel=new Button(this);cancel.setText("Отмена");cancel.setOnClickListener(v->picker.dismiss());bottom.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));panel.addView(bottom);
  ScrollView chooserScroll=new ScrollView(this);chooserScroll.addView(panel);picker.setContentView(chooserScroll);picker.show();picker.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.9),(int)(getResources().getDisplayMetrics().heightPixels*.95));
 }
 private interface Pick {void select(int action);}
 private class ControllerDiagram extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Pick pick;
  ControllerDiagram(Pick pick){super(MainActivity.this);this.pick=pick;setContentDescription("Схема Xbox: LT RT LB RB, A B X Y, крестовина, BACK START, L3 R3. Доступен текстовый список ниже.");}
  protected void onDraw(Canvas canvas){super.onDraw(canvas);float w=getWidth(),h=getHeight();paint.setColor(Color.rgb(33,43,57));canvas.drawRoundRect(new RectF(w*.08f,h*.25f,w*.92f,h*.94f),h*.16f,h*.16f,paint);
   for(int i=0;i<XboxDiagram.ACTIONS.length;i++){float[] b=XboxDiagram.BOXES[i];int a=XboxDiagram.ACTIONS[i];paint.setColor(a==8?Color.rgb(30,132,76):a==7?Color.rgb(159,42,49):a==9?Color.rgb(35,93,164):a==10?Color.rgb(170,139,24):Color.rgb(69,84,105));canvas.drawRoundRect(new RectF(b[0]*w,b[1]*h,b[2]*w,b[3]*h),dp(7),dp(7),paint);String label=ControlModel.label(a);if(a>=15)label=new String[]{"↑","↓","←","→"}[a-15];float size=Math.min(dp(20),(b[3]-b[1])*h*.48f);paint.setTextSize(size);while(paint.measureText(label)>(b[2]-b[0])*w*.88f){size*=.92f;paint.setTextSize(size);}paint.setTextAlign(Paint.Align.CENTER);paint.setColor(Color.WHITE);canvas.drawText(label,(b[0]+b[2])*w/2,(b[1]+b[3])*h/2-(paint.ascent()+paint.descent())/2,paint);}
  }
  private final DiagramTap tap=new DiagramTap();
  public boolean onTouchEvent(MotionEvent event){
   int kind=event.getActionMasked(),index=event.getActionIndex();
   if(kind==MotionEvent.ACTION_DOWN)tap.begin(event.getPointerId(index),event.getX(index),event.getY(index),getWidth(),getHeight(),ViewConfiguration.get(MainActivity.this).getScaledTouchSlop());
   else if(kind==MotionEvent.ACTION_MOVE){for(int i=0;i<event.getPointerCount();i++)tap.move(event.getPointerId(i),event.getX(i),event.getY(i));}
   else if(kind==MotionEvent.ACTION_UP){int action=tap.end(event.getPointerId(index),event.getX(index),event.getY(index));if(action!=0){performClick();pick.select(action);}}
   else if(kind==MotionEvent.ACTION_CANCEL||kind==MotionEvent.ACTION_POINTER_DOWN||kind==MotionEvent.ACTION_POINTER_UP)tap.cancel();
   return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
 }
 private boolean permitted(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
 private void loadDevices(){if(devicePicker==null)return;if(!permitted()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},42);return;}if(connection!=null)return;
  paired.clear();ArrayList<String> names=new ArrayList<>();BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
  try{if(adapter==null||!adapter.isEnabled()){feedback="Включите Bluetooth в настройках телефона";}else for(BluetoothDevice d:adapter.getBondedDevices()){paired.add(d);names.add(d.getName()+" / "+d.getAddress());}}catch(SecurityException e){feedback="Разрешите устройства поблизости";}
  if(names.isEmpty())names.add("Нет сопряжённых устройств");devicePicker.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));String address=prefs.getString("pcAddress","");for(int i=0;i<paired.size();i++)if(paired.get(i).getAddress().equals(address))devicePicker.setSelection(i);
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==42){if(permitted())loadDevices();else{feedback="Доступ Bluetooth запрещён в настройках приложения";Toast.makeText(this,feedback,Toast.LENGTH_LONG).show();}}}
 private void startConnection(){if(!permitted()){loadDevices();return;}int index=devicePicker.getSelectedItemPosition();String code=codeField.getText().toString().trim().toUpperCase(Locale.ROOT);if(index<0||index>=paired.size()||code.isEmpty()){feedback="Выберите ПК и введите код приёмника";return;}
  BluetoothDevice device=paired.get(index);prefs.edit().putString("token",code).putString("pcAddress",device.getAddress()).apply();neutral();Connection c=new Connection(device,code);connection=c;feedback="Подключение Bluetooth…";c.start();
 }
 private void stopConnection(){Connection old=connection;connection=null;neutral();if(old!=null)old.close();feedback="Отключено. ПК обнулит управление не позже тайм-аута0,4с.";}
 private class Connection {
  final BluetoothDevice device;final String code,session=UUID.randomUUID().toString();final LinkHealth health=new LinkHealth(session);final long started=SystemClock.elapsedRealtime();
  volatile boolean active=true;volatile BluetoothSocket socket;volatile long lastReply=started;String lastError="";
  Connection(BluetoothDevice device,String code){this.device=device;this.code=code;}
  void start(){new Thread(()->run(),"phone-wheel-send").start();}
  void close(){active=false;health.close();synchronized(signal){signal.notifyAll();}BluetoothSocket s=socket;if(s!=null)try{s.close();}catch(IOException ignored){}}
  void fail(String message){close();ui.post(()->{if(connection==this){connection=null;neutral();feedback="Bluetooth: "+message;Toast.makeText(MainActivity.this,feedback,Toast.LENGTH_LONG).show();}});}
  void run(){Thread reader=null;
   try{
    BluetoothSocket s=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));socket=s;if(!active){s.close();return;}s.connect();if(!active)return;
    InputStream input=s.getInputStream();OutputStream output=s.getOutputStream();lastReply=SystemClock.elapsedRealtime();
    reader=new Thread(()->read(input),"phone-wheel-ack");reader.start();long seq=0,lastSent=lastReply-20,seen=-1,seenTouch=-1;
    while(active&&connection==this){
     long now=SystemClock.elapsedRealtime();
     if(SendTiming.delay(now,lastSent,seen!=revision,seenTouch!=touchRevision)==0){
      long version=revision,touchVersion=touchRevision;ControlModel.State state=model.state(now);long packetSeq=seq++;seen=version;seenTouch=touchVersion;JSONObject data=new JSONObject();data.put("token",code);data.put("session",session);data.put("seq",packetSeq);data.put("steer",state.steer);data.put("gas",state.gas);data.put("brake",state.brake);data.put("up",state.up);data.put("down",state.down);data.put("nitro",state.nitro);data.put("handbrake",state.handbrake);data.put("buttons",state.buttons);
      health.sent(packetSeq,SystemClock.elapsedRealtime());output.write((data.toString()+"\n").getBytes("UTF-8"));lastSent=SystemClock.elapsedRealtime();
     }
     if(SystemClock.elapsedRealtime()-lastReply>1200)throw new IOException("ПК не подтверждает команды");
     synchronized(signal){if(active&&connection==this){long wait=SendTiming.delay(SystemClock.elapsedRealtime(),lastSent,seen!=revision,seenTouch!=touchRevision);if(wait>0)signal.wait(wait);}}
    }
   }catch(Exception e){if(active)fail(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
   finally{close();if(reader!=null)try{reader.join(300);}catch(InterruptedException ignored){}}
  }
  void read(InputStream input){
   new AckLineReader(input,()->active&&connection==this,line->{
    JSONObject ack;try{ack=new JSONObject(line);}catch(org.json.JSONException malformed){throw new AckLineReader.InvalidLine();}long now=SystemClock.elapsedRealtime();
    if(ack.optBoolean("ok")){if(health.ack(ack.optString("session"),ack.optLong("seq",-1),now))lastReply=now;}
    else if(ack.has("error")){lastReply=now;final String message=ack.optString("error");if(!message.equals(lastError)){lastError=message;ui.post(()->{if(connection==this)feedback=message;});}}
   },error->{if(active&&connection==this)fail(error.getMessage()==null?"Ошибка подтверждения":error.getMessage());}).run();
  }

 }
 @Override protected void onResume(){super.onResume();resumed=true;center=rawAngle;model.block(menu!=null&&menu.isShowing());changed();Sensor sensor=sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);if(sensor!=null)sensors.registerListener(this,sensor,5000);fullscreen();}
 @Override protected void onPause(){resumed=false;model.block(true);changed();stopConnection();sensors.unregisterListener(this);super.onPause();}
 @Override protected void onDestroy(){stopConnection();ui.removeCallbacksAndMessages(null);super.onDestroy();}
 @Override public void onSensorChanged(SensorEvent event){int rotation=getWindowManager().getDefaultDisplay().getRotation();double x=event.values[0],y=event.values[1];rawAngle=rotation==Surface.ROTATION_270?Math.toDegrees(Math.atan2(-y,-x)):Math.toDegrees(Math.atan2(y,x));double target=Steering.target(rawAngle,center);if(resumed&&(menu==null||!menu.isShowing())&&model.mode()==ControlModel.GYRO&&Math.abs(target-lastTarget)>.0001){lastTarget=target;model.gyro(target);changed(false);}}
 @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
