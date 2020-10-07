package com.example.shq.subjecttimetable.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Looper;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import com.example.shq.subjecttimetable.helper.CourseTableHelper;
import com.example.shq.subjecttimetable.helper.MyDbOpenHelper;
import com.example.shq.subjecttimetable.other.Exam;
import com.example.shq.subjecttimetable.other.ListExamPopup;
import com.example.shq.subjecttimetable.other.MySubjectBean;
import com.example.shq.subjecttimetable.R;
import com.example.shq.subjecttimetable.helper.SharedPref;
import com.example.shq.subjecttimetable.helper.ToastHelper;
import com.example.shq.subjecttimetable.other.ListCoursePopup;
import com.example.shq.subjecttimetable.other.XuefenjiPopup;
import com.zhuangfei.timetable.core.OnSubjectItemClickListener;
import com.zhuangfei.timetable.core.SubjectBean;
import com.zhuangfei.timetable.core.TimetableView;

import org.angmarch.views.NiceSpinner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.example.shq.subjecttimetable.helper.CourseTableHelper.htmlStringToMySubjectBeanList;

public class CourseTableActivity extends AppCompatActivity implements OnSubjectItemClickListener {

    private BottomNavigationView bottomNavigationView;
    private ViewPagerAdapter viewPagerAdapter;
    private ViewPager viewPager;
    private MenuItem menuItem;


    private TimetableView mTimetableView;//显示课表控件
    private SharedPref sharedPref;
    private NiceSpinner sp_week;//下拉列表
    private int CurWeek;
    private List<SubjectBean> subjectBeans=new ArrayList<>();
    List<MySubjectBean> mySubjectBeans=new ArrayList<>();

    private LinearLayout moreLayout;//更多按钮
    private LinearLayout backLayout;//返回按钮
    private OkHttpClient client;

    private MyDbOpenHelper myDbHelper;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_table);

        backLayout = (LinearLayout) findViewById(R.id.id_back);
        backLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                System.exit(0);
            }
        });

        sp_week=(NiceSpinner)findViewById(R.id.sp_week);
        sp_week.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean isCurWeek=false;
                if(position+1==15)
                    isCurWeek=true;
                mTimetableView.changeWeek(position+1,isCurWeek);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mTimetableView = (TimetableView) findViewById(R.id.id_timetableView);//获取控件


        sharedPref = SharedPref.getInstance(getApplicationContext());
/*        String str=sharedPref.getString("courseStr",null);//获取登录成功时存的课表html页面String
        mySubjectBeans= htmlStringToMySubjectBeanList(str);//解析String*/
        myDbHelper=new MyDbOpenHelper(this);
        String t=myDbHelper.getUser(sharedPref.getString("username",null),
                sharedPref.getString("password",null));




        int cW=sharedPref.getInt("CurWeek",-1);
        int tCW=sharedPref.getInt("TimeCurWeek",-1);
        CurWeek=getTimeCurWeek()-tCW+cW;
        //设置当前周对话框
        if(CurWeek==getTimeCurWeek()) {
            setCurWeek();
        }else {
            showTable();
        }
    }

    public void showTable(){
        mySubjectBeans=myDbHelper.getCourseTableByUsername(sharedPref.getString("username",null));
        subjectBeans=transform(mySubjectBeans);//转换
        mTimetableView.setDataSource(subjectBeans)
                .setCurTerm("大三上学期")
                .setMax(true)
                .setCurWeek(CurWeek)
                .setOnSubjectItemClickListener(this)
                .showTimetableView();
        setSp_week();
        mTimetableView.changeWeek(CurWeek, true);

    }

    /**
     * 自定义转换规则,将自己的课程对象转换为所需要的对象集合
     *
     * @param mySubjects
     * @return
     */
    public List<SubjectBean> transform(List<MySubjectBean> mySubjects) {
        //待返回的集合
        List<SubjectBean> subjectBeans = new ArrayList<>();

        //保存课程名、颜色的对应关系
        Map<String, Integer> colorMap = new HashMap<>();
        int colorCount = 1;

        //开始转换
        for (int i = 0; i < mySubjects.size(); i++) {
            MySubjectBean mySubject = mySubjects.get(i);
            //计算课程颜色
            int color;
            if (colorMap.containsKey(mySubject.getName())) {
                color = colorMap.get(mySubject.getName());
            } else {
                colorMap.put(mySubject.getName(), colorCount);
                color = colorCount;
                colorCount++;
            }
            //转换
            subjectBeans.add(new SubjectBean(mySubject.getName(), mySubject.getRoom(), mySubject.getTeacher(), mySubject.getWeekList(),
                    mySubject.getStart(), mySubject.getStep(), mySubject.getDay(), color, mySubject.getTime()));
        }
        return subjectBeans;
    }

    /**
     * 设置当前周对话框
     */
    private void setCurWeek(){
        final String items[] = getResources().getStringArray(R.array.weekArrs);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setIcon(R.drawable.ic_week)//设置标题的图片
                .setTitle("设置当前周")//设置对话框的标题
                .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CurWeek=which+1;
                        sharedPref.putInt("CurWeek",which+1);
                        sharedPref.putInt("TimeCurWeek",getTimeCurWeek());
                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ToastHelper.showToast(getApplicationContext(),"设置成功！",0);
                        showTable();
                        dialog.dismiss();
                    }
                }).create();
        dialog.show();
    }

    /**
     * 设置修改周次下拉列表
     */
    private void setSp_week(){
        String[] weekArrs = getResources().getStringArray(R.array.weekArrs);
        List<String> dataset = new ArrayList<>();
        for(int i=0;i<weekArrs.length;i++){
            if(i+1==CurWeek)
                weekArrs[i]+="(当前周)";
            dataset.add(weekArrs[i]);
        }


        sp_week.attachDataSource(dataset);

        for(int i=0;i<20;i++){
            if(i+1==CurWeek){
                sp_week.setSelectedIndex(i);
                break;
            }
        }
    }

    public static int getTimeCurWeek()
    {
        Calendar cal = Calendar.getInstance();
        int week = cal.get(cal.WEEK_OF_YEAR);
        return week;
    }

    /**
     * Item点击处理
     *
     * @param subjectList 该Item处的课程集合
     */
    @Override
    public void onItemClick(View v, List<SubjectBean> subjectList) {

        myOnItemClick(subjectList);
    }
    private void myOnItemClick(List<SubjectBean> subjectList){
        int size = subjectList.size();
        final ListCoursePopup.Builder builder=new ListCoursePopup.Builder(this);
        for (int i = 0; i < size; i++) {
            for(int j=0;j<mySubjectBeans.size();j++){
                if(subjectList.get(i).getName().compareTo(mySubjectBeans.get(j).getName())==0&&
                        subjectList.get(i).getDay()==mySubjectBeans.get(j).getDay()&&
                        subjectList.get(i).getStart()==mySubjectBeans.get(j).getStart()&&
                        subjectList.get(i).getStep()==mySubjectBeans.get(j).getStep()
                        ){
                    builder.addItem(mySubjectBeans.get(j));
                }
            }
        }
        ListCoursePopup popup = new ListCoursePopup(this,builder);
        popup.setOnListPopupItemClickListener(new ListCoursePopup.OnListPopupItemClickListener(){
            @Override
            public void onItemClick(int what) {

                goToCourseActivity("edit",String.valueOf(builder.getItemEventList().get(what).get_id()));

            }
        });
        popup.showPopupWindow();
    }

    private void showPopupListExam(List<Exam> examList){
        ListExamPopup.Builder examBuilder=new ListExamPopup.Builder(this);
        for (int i=0;i<examList.size();i++){
            examBuilder.addItem(examList.get(i));
        }
        ListExamPopup listExamPopup=new ListExamPopup(this,examBuilder);
        listExamPopup.showPopupWindow();
    }

    public void showXueFenJi(){
       // http://172.16.64.236/student/xuefenji.asp?type=0&lwPageSize=1000&lwBtnquery=%B2%E9%D1%AF
        client=new OkHttpClient.Builder()
                .cookieJar(new CookieJar() {
                    private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url, cookies);
                        cookieStore.put(HttpUrl.parse("http://xk.cacacai.cn:8080/student/public/login.asp"), cookies);
                        for(Cookie cookie:cookies){
                            System.out.println("cookie Name:"+cookie.name());
                            System.out.println("cookie Path:"+cookie.path());
                        }
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(HttpUrl.parse("http://xk.cacacai.cn:8080/student/public/login.asp"));
                        if(cookies==null){
                            System.out.println("没加载到cookie");
                        }
                        return cookies != null ? cookies : new ArrayList<Cookie>();
                    }
                })
                .build();//设置保存cookie

        RequestBody formBody = new FormBody.Builder()
                .add("username", sharedPref.getString("username",null))
                .add("passwd", sharedPref.getString("password",null))
                .add("login","%B5%C7%A1%A1%C2%BC")
                .add("mCode","000703")
                .build();//创建网络请求表单

        final Request request = new Request.Builder()
                .url(this.getString(R.string.url_login))
                .post(formBody)
                .build();//创建网络请求request
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {


                RequestBody formBody1 = new FormBody.Builder()
                        .add("type", "0")
                        .add("lwPageSize", "1000")
                        .add("lwBtnquery","%B2%E9%D1%AF")
                        .build();//创建网络请求表单
                Request request = new Request.Builder()
                        .url(getApplicationContext().getString(R.string.url_xuefenji))
                        .post(formBody1)
                        .build();//创建网络请求request
                call = client.newCall(request);
                call.enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {

                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {

                        byte[] data=response.body().bytes();
                        String str = new String(data, "gb2312");
                        List<String> stringList=CourseTableHelper.getAllSatisfyStr(str,
                                "<B>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<font face='黑体'>(.*?)</font></B></td><th>入学至今</th></tr>");
                        final String strXFJ=CourseTableHelper.subString(stringList.get(0),"face='黑体'>","</font>");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showXuefenjiPopup(strXFJ);
                            }
                        });
                        response.body().close();
                    }
                });
            }
        });
    }
    private void showXuefenjiPopup(String xueFenJi){
        XuefenjiPopup xuefenjiPopup=new XuefenjiPopup(this,xueFenJi);
        xuefenjiPopup.showPopupWindow();
    }


    public void goToCourseActivity(String action,String id){
        //新建一个显式意图，第一个参数为当前Activity类对象，第二个参数为你要打开的Activity类
        Intent intent =new Intent(CourseTableActivity.this,CourseActivity.class);
        //用Bundle携带数据
        Bundle bundle=new Bundle();
        //传递name参数为tinyphp
        bundle.putString("action", action);
        bundle.putString("id",id);
        intent.putExtras(bundle);
        startActivity(intent);
    }
}
