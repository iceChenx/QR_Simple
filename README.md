# QR_Simple
精简的二维码扫描。
```
//启动二维码扫描Activity
Intent intent = new Intent(this,CaptureActivity.class);
startActivityForResult(intent,resultCode);

```

```
//在扫描完成后，可以在onActivityResult()中获得结果

intent.getStringExtra("codedContent");//获得二维码解码后的String

intent.getParcelableExtra();//获得扫描到的Bitmap，比较模糊，因为是略缩图

```
当然，通过修改该Model的内容可轻松实现定制，注释很详细。
