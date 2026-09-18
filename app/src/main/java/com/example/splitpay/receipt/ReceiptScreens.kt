package com.example.splitpay.receipt
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.example.splitpay.ui.*
import com.example.splitpay.data.categories
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File

@Composable fun ReceiptScannerScreen(scanned:(String,String)->Unit) {
 val context=LocalContext.current
 val lifecycle=LocalLifecycleOwner.current
 val scope=rememberCoroutineScope()
 var granted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
 androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME){granted=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it}
 var error by remember{mutableStateOf<String?>(null)}
 var busy by remember{mutableStateOf(false)}
 var ready by remember{mutableStateOf(false)}
 val preview=remember{PreviewView(context)}
 val capture=remember{ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()}
 val recognizer=remember{TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)}
 var provider by remember{mutableStateOf<ProcessCameraProvider?>(null)}
 fun recognize(uri:Uri){scope.launch{busy=true;error=null;try{val result=recognizer.process(InputImage.fromFilePath(context,uri)).await();require(result.text.isNotBlank()){"No text detected. Try better lighting or a closer photo."};scanned(result.text,uri.toString())}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message ?: "Could not read the receipt."}finally{busy=false}}}
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let{recognize(it)}}
 DisposableEffect(granted,lifecycle){
  var disposed=false
  if(granted){val future=ProcessCameraProvider.getInstance(context);future.addListener({
   if(!disposed)try{provider=future.get();val useCase=Preview.Builder().build().also{it.setSurfaceProvider(preview.surfaceProvider)};provider?.unbindAll();provider?.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,useCase,capture);ready=true}catch(e:Exception){error="Camera unavailable. You can choose a receipt image instead."}
  },ContextCompat.getMainExecutor(context))}
  onDispose{disposed=true;provider?.unbindAll();ready=false}
 }
 DisposableEffect(Unit){onDispose{recognizer.close()}}
 Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("Scan a receipt",style=MaterialTheme.typography.headlineMedium)
  Text("Keep the full receipt in view. You will review all fields before saving.")
  if(granted) AndroidView(factory={preview},modifier=Modifier.fillMaxWidth().weight(1f)) else {
   Button(onClick={permission.launch(Manifest.permission.CAMERA)}){Text("Allow camera access")}
   OutlinedButton(onClick={context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+context.packageName)))}){Text("Open permission settings")}
   Spacer(Modifier.weight(1f))
  }
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  Button(onClick={
   busy=true;error=null
   val file=File(context.cacheDir,"receipt-"+System.currentTimeMillis()+".jpg")
   capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(context),object:ImageCapture.OnImageSavedCallback{
    override fun onImageSaved(result:ImageCapture.OutputFileResults){recognize(Uri.fromFile(file))}
    override fun onError(exception:ImageCaptureException){busy=false;error="Capture failed. Please try again."}
   })
  },enabled=granted&&ready&&!busy,modifier=Modifier.fillMaxWidth()){Text("Capture & read receipt")}
  OutlinedButton(onClick={gallery.launch("image/*")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Choose receipt image")}
 }
}
@Composable fun ReceiptReviewScreen(receipt:ReceiptViewModel,model:MainViewModel,saved:()->Unit,split:()->Unit) {
 val draft by receipt.draft.collectAsStateWithLifecycle()
 var merchant by rememberSaveable(draft.text){mutableStateOf(draft.merchant)}
 var amount by rememberSaveable(draft.text){mutableStateOf(draft.amount)}
 var date by rememberSaveable(draft.text){mutableStateOf(draft.date)}
 var category by rememberSaveable(draft.text){mutableStateOf(draft.category)}
 val busy by model.saving.collectAsStateWithLifecycle()
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  item{Text("Review receipt",style=MaterialTheme.typography.headlineMedium);Text("OCR can make mistakes. Confirm the merchant, total and date.")}
  if(draft.image.isNotBlank())item{coil.compose.AsyncImage(model=draft.image,contentDescription="Captured receipt",modifier=Modifier.fillMaxWidth().height(180.dp))}
  item{OutlinedTextField(merchant,{merchant=it},label={Text("Merchant")},modifier=Modifier.fillMaxWidth())}
  item{OutlinedTextField(amount,{amount=it},label={Text("Total (INR)")},modifier=Modifier.fillMaxWidth())}
  item{OutlinedTextField(date,{date=it},label={Text("Date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())}
  item{ChoiceField("Category",category,categories.associateWith{it}){category=it}}
  item{TextButton(onClick={model.classify(merchant){category=it}},enabled=merchant.isNotBlank()){Text("Suggest category")}}
  item{Button(onClick={model.save(merchant,amount,category,date,"Receipt scan",saved,source="RECEIPT")},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Save personal expense")}}
  item{OutlinedButton(onClick={receipt.update(draft.copy(merchant=merchant,amount=amount,date=date,category=category));split()},enabled=!busy&&amount.toBigDecimalOrNull()?.signum()==1&&merchant.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Split with a group")}}
  item{Text("Extracted text",style=MaterialTheme.typography.titleMedium);Text(draft.text)}
 }
}
