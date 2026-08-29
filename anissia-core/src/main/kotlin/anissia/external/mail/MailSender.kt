package anissia.external.mail

import anissia.support.Json
import anissia.support.logger
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.io.File
import java.util.Properties

@Component
class MailSender(
    @Value("\${env}") env: String,
) {
    private val log = logger<MailSender>()
    private val configFile = File(CONFIG_FILE)
    private val enabled = configFile.exists()
    private val props: Map<String, String> = if (enabled) Json.read(configFile) else mapOf()
    private val sender = JavaMailSenderImpl()

    init {
        if (enabled) {
            sender.username = props["username"]
            sender.password = props["password"]
            sender.javaMailProperties = Properties().apply {
                setProperty("mail.transport.protocol", "smtp")
                setProperty("mail.smtp.starttls.enable", "true")
                setProperty("mail.smtp.ssl.trust", props["host"] ?: "")
                setProperty("mail.smtp.host", props["host"])
                setProperty("mail.smtp.auth", "true")
                setProperty("mail.smtp.port", props["port"])
                setProperty("mail.smtp.socketFactory.port", props["port"])
                setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
                setProperty("mail.smtp.starttls.required", "true")
                setProperty("mail.debug", "true")
                setProperty("mail.smtp.ssl.enable", "true")
            }
        } else if (env != "local") {
            throw IllegalStateException("$CONFIG_FILE is required when env is '$env'")
        }
    }

    @Async
    fun sendAsync(to: String, subject: String, htmlContent: String) = send(to, subject, htmlContent)

    fun send(to: String, subject: String, htmlContent: String) = send(listOf(to), listOf(), subject, htmlContent)

    fun send(to: List<String>, cc: List<String>, subject: String, htmlContent: String) {
        if (!enabled) {
            log.info(
                "EMAIL DEVELOP MODE\nto: {}\ncc: {}\nsubject: {}\ncontent: {}",
                to,
                cc,
                subject,
                htmlContent,
            )
            return
        }
        try {
            sender.send { message ->
                message.setFrom(InternetAddress(props["from"]))
                to.forEach { message.addRecipient(Message.RecipientType.TO, InternetAddress(it)) }
                cc.forEach { message.addRecipient(Message.RecipientType.CC, InternetAddress(it)) }
                message.subject = subject
                message.setText(htmlContent, "UTF-8", "html")
            }
        } catch (e: Exception) {
            log.error(
                "EMAIL ERROR\nto: $to\ncc: $cc\nsubject: $subject\ncontent: $htmlContent",
                e,
            )
        }
    }

    companion object {
        private const val CONFIG_FILE = "./email.json"
    }
}
