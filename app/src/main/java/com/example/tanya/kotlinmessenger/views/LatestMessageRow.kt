package com.example.tanya.kotlinmessenger.views

import com.example.tanya.kotlinmessenger.R
import com.example.tanya.kotlinmessenger.models.ChatMessage
import com.example.tanya.kotlinmessenger.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.squareup.picasso.Picasso
import com.xwray.groupie.Item
import com.xwray.groupie.ViewHolder
import kotlinx.android.synthetic.main.latest_message_row.view.*

class LatestMessagesRow(val chatMessage: ChatMessage): Item<ViewHolder>(){
    var chatPartnerUser: User? = null

    override fun bind(viewHolder: ViewHolder, position: Int) {
        if(chatMessage.text.length < 20){
            viewHolder.itemView.message_textview_latest_message.text = chatMessage.text
        } else{
            viewHolder.itemView.message_textview_latest_message.text = chatMessage.text.substring(0, 20) + "..."
        }
        val chatPaetnerId: String
        if(chatMessage.fromId == FirebaseAuth.getInstance().uid){
            chatPaetnerId = chatMessage.toId
        } else {
            chatPaetnerId = chatMessage.toId
        }
        val ref = FirebaseDatabase.getInstance().getReference("/users/$chatPaetnerId")
            .addListenerForSingleValueEvent(object: ValueEventListener {

                override fun onDataChange(p0: DataSnapshot) {
                    chatPartnerUser = p0.getValue(User::class.java)
                    viewHolder.itemView.username_textview_latest_message.text = chatPartnerUser?.username

                    val targetImageView = viewHolder.itemView.imageview_latest_message
                    Picasso.get().load(chatPartnerUser?.profileImageUrl).into(targetImageView)
                }
                override fun onCancelled(p0: DatabaseError) {}
            })
    }
    override fun getLayout(): Int {
        return R.layout.latest_message_row
    }
}
