package com.rdv_antony.bot.service;

import com.rdv_antony.bot.config.BotConfig;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    final BotConfig botConfig;
    public TelegramBot(@Value("${bot.token}") String botToken, BotConfig botConfig) {
        super(botToken);
        this.botConfig = botConfig;
    }
    @Override
    public void onUpdateReceived(Update update) {

        if(update.hasMessage() && update.getMessage().hasText()){
            String messageText = update.getMessage().getText();
            if(Objects.equals(update.getMessage().getChatId(), botConfig.getChatId())){
                if ("/start".equals(messageText)) {
                    // Handle user command to start website checking
                    startWebsiteChecking(update.getMessage().getChatId());
                }else if ("/showlog".equals(messageText)) {
                    executorService.submit(() -> {
                        String logFilePath = "/var/log/com.rdv_antony.bot/app.log";
                        String logData = getLogTail(update.getMessage().getChatId(), logFilePath, 15);
                        sendMessage(update.getMessage().getChatId(), logData);
                    });
                }else if ("/takerdv4".equals(messageText)) {
                    executorService.submit(() -> {
                        try {
                            checkWebsite(update.getMessage().getChatId(), 1, true);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }else if ("/takerdv5".equals(messageText)) {
                    executorService.submit(() -> {
                        try {
                            checkWebsite(update.getMessage().getChatId(), 2, true);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }else{
                sendMessage(update.getMessage().getChatId(), "Unknown user.");
            }

        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    // Method to start website checking
    private void startWebsiteChecking(long chatId) {
        sendMessage(chatId, "Monitoring guichet 4...");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Info: Starting to monitor guichet 4");
                checkWebsite(chatId, 1, false);
            } catch (InterruptedException e) {
                log.error("Error: " + e.getMessage());
            }

        }, 0, 5, TimeUnit.MINUTES); // Adjust the timing as needed (e.g., check every 5 minutes)
        sendMessage(chatId, "Monitoring guichet 5...");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Info: Starting to monitor guichet 5");
                checkWebsite(chatId, 2, false);
            } catch (InterruptedException e) {
                log.error("Error: " + e.getMessage());
            }

        }, 2, 5, TimeUnit.MINUTES); // Adjust the timing as needed (e.g., check every 5 minutes)
    }


    private void checkWebsite(long chatId, int number, boolean takeRdv) throws InterruptedException {

        WebDriver driver = null;
        try {
            FirefoxOptions options = new FirefoxOptions();
            options.addArguments("--headless");

            driver = new FirefoxDriver(options);
            String url = botConfig.getUrl();
            driver.get(url);

            Thread.sleep(1000);

            WebElement checkboxElement = driver.findElement(By.id("condition"));

            // Check the checkbox if it's not already checked
            if (!checkboxElement.isSelected()) {
                checkboxElement.click();
                Thread.sleep(1000);
            }

            try {
                // Attempt to find the button with the specified ID
                WebElement cookieButton = driver.findElement(By.id("tarteaucitronAllDenied2"));

                if (cookieButton.isDisplayed() && cookieButton.isEnabled()) {
                    // Button exists and is clickable, so click it
                    cookieButton.click();
                    Thread.sleep(1000);
                }

            } catch (NoSuchElementException ignored) {

            }

            // Find the button element and click it
            WebElement buttonElement = driver.findElement(By.name("nextButton"));
            buttonElement.click();

            // Wait for the new page to load (you may need to adjust the timeout)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5)); // 5 seconds timeout
            wait.until(ExpectedConditions.urlContains(url + "/1"));

            // Find the radio buttons (assuming they have unique locators)
//            WebElement radioButton = driver.findElement(By.id("planning23292"));
            Thread.sleep(2000);
            List<WebElement> radioButtons = driver.findElements(By.cssSelector("input[type='radio'][name='planning']"));
            WebElement radioButton = radioButtons.get(0);

            if (number == 2) {
                // Find the radio buttons (assuming they have unique locators)
                radioButton = radioButtons.get(1);
            }

            radioButton.click();
            Thread.sleep(1000);

            // Click the submit button
            WebElement submitButton = driver.findElement(By.name("nextButton"));
            submitButton.click();

            // Define a custom expected condition to wait for URLs containing /2 or /4 or /3
            ExpectedCondition<Boolean> urlContains2Or4 = new ExpectedCondition<Boolean>() {
                @Override
                public Boolean apply(WebDriver driver) {
                    String currentUrl = driver.getCurrentUrl();
                    return currentUrl != null && (currentUrl.contains(url+"/2") || currentUrl.contains(url+"/4") || currentUrl.contains(url+"/3"));
                }
            };

            List<String> expectedUrls = Arrays.asList(url + "/2", url + "/4",  url + "/3");
            // Wait for the new page to load (you may need to adjust the timeout)
            WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(40));
            wait2.until(urlContains2Or4);

            // Get the current URL after the page is fully loaded
            String currentUrl = driver.getCurrentUrl();

            // Check which URL you are on and take different actions accordingly
            if (expectedUrls.contains(currentUrl)) {
                if (currentUrl.equals(url+"/4")) {
                    makeAction(chatId, number, takeRdv, driver, url);

                } else if (currentUrl.equals(url+"/3")) {
                    WebElement nextButton = driver.findElement(By.name("nextButton"));
                    nextButton.click();

                    makeAction(chatId, number, takeRdv, driver, url);
                }
                else if (driver.getPageSource().contains("Il n'existe plus de plage horaire libre pour votre demande de rendez-vous. Veuillez recommencer ultérieurement.")) {
                    log.info("No places for guichet " + (number == 1 ? "4" : "5"));
                }
            } else {
                log.info("Redirected from guichet " + (number == 1 ? "4" : "5"));
            }


        } catch (NoSuchElementException e) {
            log.error("Element not found: " + e.getMessage());
        } catch (TimeoutException e) {
            log.error("Timeout waiting for element: " + e.getMessage());
        } catch (InterruptedException e) {
            log.error("Thread interrupted: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error running web driver: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit(); // Ensure that the driver is always closed
            }
        }
    }

    private void makeAction(long chatId, int number, boolean takeRdv, WebDriver driver, String url) throws InterruptedException {
        Thread.sleep(3000);
        if (!takeRdv){
            // Send a notification or perform other actions specific to /4
            sendMessage(chatId, "Guichet " + (number == 1 ? "4" : "5") + ": There is place!");
            // Capture a screenshot
            File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            sendPhoto(chatId, screenshotFile);
        }else {
            sendMessage(chatId, "Guichet " + (number == 1 ? "4" : "5") + ": taking rvd...");
            // Click the submit button
            WebElement firstRdvButton = driver.findElement(By.name("nextButton"));
            firstRdvButton.click();

            sendMessage(chatId, "clicking captcha...");

            // Wait for the new page to load (you may need to adjust the timeout)
            WebDriverWait wait3 = new WebDriverWait(driver, Duration.ofSeconds(60)); // 5 seconds timeout
            wait3.until(ExpectedConditions.urlContains(url + "/6"));

            wait3.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt((By.cssSelector("iframe[name^='a-'][src^='https://www.google.com/recaptcha/api2/anchor?']"))));
            wait3.until(ExpectedConditions.elementToBeClickable((By.xpath("//span[@id='recaptcha-anchor']"))));

            WebElement captcha = driver.findElement(By.xpath("//span[@id='recaptcha-anchor']"));
            captcha.click();
            // Click the submit button

            driver.switchTo().defaultContent();
            Thread.sleep(3000);
            WebElement nextButton = driver.findElement(By.name("nextButton"));
            nextButton.click();

            sendMessage(chatId, "entering user data...");

            // Wait for the new page to load (you may need to adjust the timeout)
            WebDriverWait wait4 = new WebDriverWait(driver, Duration.ofSeconds(60)); // 5 seconds timeout
            wait4.until(ExpectedConditions.urlContains(url + "/8"));

            fillInputField(driver, "firstname", "Alina");
            fillInputField(driver, "lastname", "Novikova");
            fillInputField(driver, "email", "alinanov257@gmail.com");
            fillInputField(driver, "emailcheck", "alinanov257@gmail.com");

            nextButton = driver.findElement(By.name("nextButton"));
            nextButton.click();

            sendMessage(chatId, "rdv is taken.");
        }
    }

    // Function to fill an input field by name
    private static void fillInputField(WebDriver driver, String fieldName, String value) {
        WebElement inputElement = driver.findElement(By.name(fieldName));
        inputElement.sendKeys(value);
    }

    private void sendMessage(long chatId, String text){
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
           log.error("Error: " + e.getMessage());
        }
    }

    private void sendPhoto(long chatId, File photo){

        SendPhoto msg = new SendPhoto();
        msg.setChatId(chatId);
        msg.setPhoto(new InputFile(photo));
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Error: " + e.getMessage());
        }
    }

    private String getLogTail(long chatId, String filePath, int numLines) {
        List<String> lastLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            List<String> buffer = new ArrayList<>(numLines + 1); // +1 to hold extra line for tail

            while ((line = reader.readLine()) != null) {
                buffer.add(line);

                if (buffer.size() > numLines) {
                    buffer.remove(0); // Remove the first line if buffer exceeds numLines
                }
            }

            lastLines.addAll(buffer);
        } catch (IOException e) {
            sendMessage(chatId, "An error occurred while reading the log file: "+ e.getMessage());
        }
        return String.join("\n", lastLines);
    }

}
