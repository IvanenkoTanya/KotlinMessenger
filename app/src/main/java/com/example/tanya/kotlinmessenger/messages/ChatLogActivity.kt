package com.example.tanya.kotlinmessenger.messages

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.speech.RecognizerIntent
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tanya.kotlinmessenger.R
import com.example.tanya.kotlinmessenger.models.ChatMessage
import com.example.tanya.kotlinmessenger.models.User
import com.example.tanya.kotlinmessenger.views.ChatFromItem
import com.example.tanya.kotlinmessenger.views.ChatToItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.squareup.picasso.Picasso
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.ViewHolder
import kotlinx.android.synthetic.main.activity_chat_log.*
import kotlinx.android.synthetic.main.chat_from_row.view.*
import kotlinx.android.synthetic.main.chat_to_row.view.*
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class ChatLogActivity : AppCompatActivity() {

    val adapter = GroupAdapter<ViewHolder>()

    var toUser: User? = null
    private val REQUEST_CODE = 1234
    val MY_REQUEST_READ_GALLERY = 13
    val MY_REQUEST_WRITE_GALLERY = 14
    val MY_REQUEST_GALLERY = 15
    var filen: File? = null
    var imageUri: Uri? = null
    var imageName = ""
    private val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_log)

        recycler_view_chat_log.layoutManager = LinearLayoutManager(this)
        recycler_view_chat_log.adapter = adapter

        toUser = intent.getParcelableExtra<User>(NewMessageActivity.USER_KEY)
        supportActionBar?.title = toUser?.username

        listenForMessages()

        send_button_chat_log.setOnClickListener {
            Log.d("ChatLogActivity", "Attempt to send message...")
            performSendMessage()
        }

        // Disable button if no recognition service is present
        val pm = packageManager
        val activities = pm.queryIntentActivities(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0
        )
        if (activities.size == 0) {
            voice_message_button_chat_log.setEnabled(false)
            val toast = Toast.makeText(applicationContext, "Голосовой ввод не поддерживается!", Toast.LENGTH_SHORT)
            toast.show()
        }

        voice_message_button_chat_log.setOnClickListener{
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Voice recognition Demo...")
            startActivityForResult(intent, REQUEST_CODE)
        }

        edittext_chat_log.setOnClickListener{
            recycler_view_chat_log.scrollToPosition(adapter.itemCount - 1)
        }

        send_from_gallery_button_chat_log.setOnClickListener {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // Populate the wordsList with the String values the recognition engine thought it heard
            if (data == null) return
            val matches = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )
            edittext_chat_log.setText(matches[0].toString())
        }
        when(requestCode){
            MY_REQUEST_GALLERY -> try {
                val inputStream = contentResolver.openInputStream(data?.getData())
                filen = getFile()
                val fileOutStream = FileOutputStream(filen)
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while(true){
                    bytesRead = inputStream.read(buffer)
                    if(bytesRead == -1) break
                    fileOutStream.write(buffer, 0, bytesRead)
                }
                fileOutStream.close()
                inputStream!!.close()
                val imageUri = Uri.parse("file:///" + filen)
                if(imageUri != null){
                    //push image
                }
            } catch (e: Exception){
                // Log.d(TAG, "Error while creating temp file", e)
            }
        }
    }

    private fun listenForMessages(){
        val fromId = FirebaseAuth.getInstance().uid
        val toId = toUser?.uid
        val ref = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId")
            .addChildEventListener(object : ChildEventListener {

                override fun onChildAdded(p0: DataSnapshot, p1: String?) {
                    val chatMessage = p0.getValue(ChatMessage::class.java)

                    if(chatMessage != null) {
                        Log.d("ChatLogActivity", chatMessage.text)

                        if (chatMessage.fromId == FirebaseAuth.getInstance().uid){
                            val currentUser = LatestMessagesActivity.currentUser ?: return
                            adapter.add(ChatFromItem(chatMessage.text, currentUser))
                        } else {
                            adapter.add(ChatToItem(chatMessage.text, toUser!!))
                        }
                    }

                    recycler_view_chat_log.scrollToPosition(adapter.itemCount - 1)
                }

                override fun onCancelled(p0: DatabaseError) {}

                override fun onChildMoved(p0: DataSnapshot, p1: String?) {}

                override fun onChildChanged(p0: DataSnapshot, p1: String?) {}

                override fun onChildRemoved(p0: DataSnapshot) {}
            })
    }

    private fun performSendMessage(){
        val text = edittext_chat_log.text.toString()

        val fromId = FirebaseAuth.getInstance().uid
        val user = intent.getParcelableExtra<User>(NewMessageActivity.USER_KEY)
        val toId = user.uid

        if(fromId == null) return

        val reference = FirebaseDatabase.getInstance().getReference("/user-messages/$fromId/$toId").push()

        val toReference = FirebaseDatabase.getInstance().getReference("/user-messages/$toId/$fromId").push()

        val chatMessage = ChatMessage(reference.key!!, text, fromId!!, toId, System.currentTimeMillis() / 1000)
        reference.setValue(chatMessage)
            .addOnSuccessListener {
                Log.d("ChatLogActivity", "Saved our chat message: ${reference.key}")
                edittext_chat_log.text.clear()
                recycler_view_chat_log.scrollToPosition(adapter.itemCount - 1)
            }

        toReference.setValue(chatMessage)

        val latestMessageRef = FirebaseDatabase.getInstance().getReference("/latest-messages/$fromId/$toId")
        latestMessageRef.setValue(chatMessage)

        val latestMessageToRef = FirebaseDatabase.getInstance().getReference("/latest-messages/$toId/$fromId")
        latestMessageToRef.setValue(chatMessage)
    }

    private fun checkPermissionRG(){
        val permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
        if(permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(android.Manifest.permission.READ_EXTERNAL_STORAGE), MY_REQUEST_READ_GALLERY)
        } else{
            checkPermissionWG()
        }
    }
    fun checkPermissionWG(){
        val permissionCheck = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if(permissionCheck != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), MY_REQUEST_WRITE_GALLERY)
        } else{
            getPhotos()
        }
    }
    fun getPhotos(){
        val photoPickerIntent = Intent(Intent.ACTION_PICK)
        photoPickerIntent.type = "image/*"

        val imageFile = createImageFile()
        imageName =  imageFile.name.toString()
        imageUri = FileProvider.getUriForFile(this,
            "com.example.tanja.insta",
            imageFile)
        startActivityForResult(photoPickerIntent, MY_REQUEST_GALLERY)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when(requestCode){
            MY_REQUEST_READ_GALLERY -> checkPermissionWG()
            MY_REQUEST_WRITE_GALLERY -> getPhotos()
        }
    }
    private fun createImageFile(): File {
        val storageDir = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${simpleDateFormat.format(Date())}_",
            ".jpg",
            storageDir
        )
    }


    fun getFile(): File?{
        val fileDir = File("" + Environment.getExternalStorageDirectory() + "/Android/data" + applicationContext.packageName + "/Files")
        if(!fileDir.exists()){
            if(!fileDir.mkdirs()){
                return null
            }
        }
        return File(fileDir.getPath() + File.separator + imageName)
    }
}
