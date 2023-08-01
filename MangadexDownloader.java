package main;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MangadexDownloader {
	URL url;
	ObjectMapper mapper;
	HttpURLConnection con;
	int chapterNumber;

	MangadexDownloader() {
		mapper = new ObjectMapper();
		chapterNumber = 1;
	}

	private void createPDF(List<BufferedImage> images) throws IOException {
		PDDocument chapter = new PDDocument();
		for (BufferedImage img : images) {
			PDPage page = new PDPage(new PDRectangle(img.getWidth(), img.getHeight()));
			chapter.addPage(page);
			PDImageXObject pdImage = LosslessFactory.createFromImage(chapter, img);
			PDPageContentStream contentStream = new PDPageContentStream(chapter, page);
			contentStream.drawImage(pdImage, 0, 0);
			contentStream.close();
		}

		chapter.save("Chapter " + chapterNumber + ".pdf");
		chapter.close();
	}

	private void getChapterPages(List<String> chapterList) throws IOException {
		System.out.print("Downloading " + (double) 0 + "%\r");

		for (String id : chapterList) {
			url = new URL("https://api.mangadex.org/at-home/server/" + id);
			con = (HttpURLConnection) url.openConnection();
			int totalChapters = chapterList.size() - 1;

			JsonNode rootNode = mapper.readTree(con.getInputStream());
			String baseUrl = rootNode.get("baseUrl").asText();

			rootNode = rootNode.get("chapter");
			String hash = rootNode.get("hash").asText();
			rootNode = rootNode.get("data");

			List<BufferedImage> images = new ArrayList<BufferedImage>();
			for (JsonNode node : rootNode) {
				URL imgUrl = new URL(baseUrl + "/data/" + hash + "/" + node.asText());
				images.add(ImageIO.read(imgUrl));
			}

			createPDF(images);
			System.out.print("Downloading " + ((double) chapterNumber++ * 100 / totalChapters) + "%\r");
		}
	}

	private void findChapterID(String mangaId) throws IOException {
		url = new URL("https://api.mangadex.org/manga/" + mangaId
				+ "/feed?translatedLanguage[]=en&order[volume]=asc&order[chapter]=asc&limit=500");
		con = (HttpURLConnection) url.openConnection();

		List<String> chapterIDList = new ArrayList<String>();
		JsonNode dataArrayNode = mapper.readTree(con.getInputStream()).get("data"); // the data field from the json
		for (JsonNode node : dataArrayNode) {
			chapterIDList.add(node.get("id").asText()); // the id field in data
		}
		getChapterPages(chapterIDList);
	}

	private void findMangaID(String mangaName) throws IOException {
		mangaName = URLEncoder.encode(mangaName, "UTF-8");

		URL url = new URL("https://api.mangadex.org/manga?title=" + mangaName + "&order[relevance]=desc");
		con = (HttpURLConnection) url.openConnection();

		System.out.println(url);
		List<String> ids = new ArrayList<String>();
		List<String> descriptions = new ArrayList<String>();
		JsonNode rootNode = mapper.readTree(con.getInputStream()).get("data");
		int i = 1;
		for (JsonNode node : rootNode) {
			JsonNode en_description = node.findValue("description").get("en");
			if (en_description != null) {
				descriptions.add(en_description.asText());
				ids.add(node.get("id").asText());
				System.out.println(i++ + ". " + descriptions.get(descriptions.size() - 1));
				System.out.println();
			}
		}

		Scanner sc = new Scanner(System.in);
		System.out.println("Enter your choice: ");
		int choice = sc.nextInt();
		sc.close();
		findChapterID(ids.get(choice - 1));
	}

	public static void main(String[] args) throws IOException {
		MangadexDownloader md = new MangadexDownloader();
		md.findMangaID(args[0]);
	}
}
