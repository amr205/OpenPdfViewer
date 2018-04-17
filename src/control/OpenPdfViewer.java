package control;

import javafx.beans.NamedArg;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;

public class OpenPdfViewer extends BorderPane implements Initializable{
    @FXML
    private Pagination pagination;

    @FXML
    private AnchorPane mainPane;

    @FXML
    private BorderPane borderPane;

    @FXML
    private ToolBar toolBar;

    @FXML
    private HBox zoomOptionsBox,loadOptionsBox;

    @FXML
    private Button loadButton,reduceZoomButton,addZoomButton,zoomHeightButton,zoomWidthButton;

    @FXML
    private ScrollPane scroller;

    String file;

    String initialDirectory;

    Boolean zoomOptions, loadOptions;

    private Pdf pdf;

    private FileChooser fileChooser;

    SimpleObjectProperty<StackPane> currentImage;

    private float zoomFactor;

    public enum ZoomType{WIDTH,HEIGHT,CUSTOM};

    private ZoomType zoomType;

    public OpenPdfViewer(){
        file = "";
        pdf = null;
        zoomType = ZoomType.WIDTH;
        zoomOptions=false;
        loadOptions=false;

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("PdfView.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);
        this.getStylesheets().add(getClass().getResource("stylesheet.css").toString());
        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if(!file.isEmpty())
            pdf = new Pdf(Paths.get(file));

        initScroller();

        mainPane.heightProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number t1) {
                resize();
            }
        });

        mainPane.widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number number, Number t1) {
                resize();
            }
        });

        System.out.println(pdf==null);

        if(pdf!=null)
            pagination.setPageCount(pdf.numPages());

        pagination.setPageFactory(index ->createPdfImage(index));

        HBox.setHgrow(zoomOptionsBox, Priority.ALWAYS);
        HBox.setHgrow(loadOptionsBox, Priority.ALWAYS);

        initEventHandler();

        updateToolbar();

        initialDirectory = Paths.get(System.getProperty("user.home")).toFile().toString();

        fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(Paths.get(initialDirectory).toFile());
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf", "*.PDF"));

    }

    private void initEventHandler() {
        EventHandler<ActionEvent> eventEventHandler = new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                if(actionEvent.getSource()==loadButton){
                    final File file = fileChooser.showOpenDialog(pagination.getScene().getWindow());
                    if(file!=null)
                        loadPdf(file.getAbsolutePath());
                }
                else
                if(pdf!=null){
                    if(actionEvent.getSource()==addZoomButton){
                        setZoomType(ZoomType.CUSTOM);
                        zoomFactor*=1.05;
                        updateImage(pagination.getCurrentPageIndex());

                    }
                    else if(actionEvent.getSource()==reduceZoomButton){
                        setZoomType(ZoomType.CUSTOM);
                        zoomFactor*=.95;
                        updateImage(pagination.getCurrentPageIndex());

                    }
                    else if(actionEvent.getSource()==zoomHeightButton){
                        setZoomType(ZoomType.HEIGHT);
                        updateImage(pagination.getCurrentPageIndex());

                    }
                    else if(actionEvent.getSource()==zoomWidthButton){
                        setZoomType(ZoomType.WIDTH);
                        updateImage(pagination.getCurrentPageIndex());

                    }
                }
            }
        };

        loadButton.setOnAction(eventEventHandler);
        addZoomButton.setOnAction(eventEventHandler);
        reduceZoomButton.setOnAction(eventEventHandler);
        zoomHeightButton.setOnAction(eventEventHandler);
        zoomWidthButton.setOnAction(eventEventHandler);

    }

    public void loadPdf(String path){
        try {

            if (pdf != null)
                pdf.getDocument().close();

            pdf = new Pdf(Paths.get(path));

            updateZoomFactor();

            pagination.setPageCount(pdf.numPages());
            pagination.setCurrentPageIndex(0);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void initScroller() {
        currentImage = new SimpleObjectProperty<StackPane>();
        updateImage(0);

        scroller = new ScrollPane();
        AnchorPane.setTopAnchor(scroller,0.0);
        AnchorPane.setRightAnchor(scroller,0.0);
        AnchorPane.setLeftAnchor(scroller,0.0);
        AnchorPane.setBottomAnchor(scroller,70.0);
        scroller.contentProperty().bind(currentImage);


    }


    private Node createPdfImage(int index) {
        updateImage(index);
        return scroller;

    }

    private void updateImage(int index) {
        updateZoomFactor();

        if(scroller!=null)
            scroller.setVvalue(0);

        StackPane stackPane = new StackPane();
        stackPane.setMinWidth(borderPane.getWidth()-20);

        ImageView imageView = new ImageView();

        if(pdf!=null){
            Image image = pdf.getImage(index,zoomFactor);
            if(image!=null)
                imageView.setImage(image);
        }

        stackPane.getChildren().add(imageView);

        currentImage.set(stackPane);
    }

    private void resize(){
        updateZoomFactor();
        updateImage(pagination.getCurrentPageIndex());
    }

    private void updateZoomFactor(){
        if(zoomType==ZoomType.WIDTH){
            if(pdf!=null&&pdf.getImage(pagination.getCurrentPageIndex())!=null){
                zoomFactor=(float)((mainPane.getWidth()-20)/pdf.getImage(pagination.getCurrentPageIndex()).getWidth());
            }
        }
        else if(zoomType==ZoomType.HEIGHT){
            if(pdf!=null&&pdf.getImage(pagination.getCurrentPageIndex())!=null)
                zoomFactor=(float)((mainPane.getHeight()-70)/pdf.getImage(pagination.getCurrentPageIndex()).getHeight());
        }
    }

    private void updateToolbar() {

        if(zoomOptions||loadOptions){
            if(borderPane.getTop()==null)
                borderPane.setTop(toolBar);
        }

        if(!loadOptions){
            toolBar.getItems().remove(loadOptionsBox);
        }
        else {
            if(!toolBar.getItems().contains(loadOptionsBox))
                toolBar.getItems().add(loadOptionsBox);
        }

        if(!zoomOptions){
            toolBar.getItems().remove(zoomOptionsBox);
        }
        else {
            if(!toolBar.getItems().contains(zoomOptionsBox))
                toolBar.getItems().add(zoomOptionsBox);
        }

        if(zoomOptions&&loadOptions){
            if(toolBar.getItems().get(0).equals(zoomOptionsBox)){
                toolBar.getItems().clear();
                toolBar.getItems().add(loadOptionsBox);
                toolBar.getItems().add(zoomOptionsBox);
            }
        }

        if(!zoomOptions&&!loadOptions){
            borderPane.setTop(null);
        }

    }


    public void setFile(String file) {
        this.file = file;
        System.out.println(file);
        loadPdf(file);
    }

    public String getFile() {
        return file;
    }

    public ZoomType getZoomType() {
        return zoomType;
    }

    public void setZoomType(ZoomType zoomType) {
        this.zoomType = zoomType;
        updateZoomFactor();
    }

    public Boolean getZoomOptions() {
        return zoomOptions;
    }

    public void setZoomOptions(Boolean zoomOptions) {
        this.zoomOptions = zoomOptions;
        updateToolbar();
    }

    public Boolean getLoadOptions() {
        return loadOptions;
    }

    public void setLoadOptions(Boolean loadOptions) {
        this.loadOptions = loadOptions;
        updateToolbar();
    }


    public String getInitialDirectory() {
        return initialDirectory;
    }

    public void setInitialDirectory(String initialDirectory) {
        this.initialDirectory = initialDirectory;
        fileChooser.setInitialDirectory(Paths.get(initialDirectory).toFile());
    }

}

class Pdf{
    private PDDocument document;
    private PDFRenderer renderer;

    public Pdf(Path path) {
        try {
            document = PDDocument.load(path.toFile());
            renderer = new PDFRenderer(document);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public int numPages() {
        return document.getPages().getCount();
    }

     public Image getImage(int pageNumber) {
        BufferedImage pageImage = null;
        try {
            if(pageNumber<=document.getNumberOfPages())
                pageImage = renderer.renderImage(pageNumber);
            else
                return null;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return SwingFXUtils.toFXImage(pageImage, null);
    }

    public Image getImage(int pageNumber, float scaleFactor) {
        BufferedImage pageImage;
        try {
            if(pageNumber<=document.getNumberOfPages()) {
                if (scaleFactor >= 0.1)
                    pageImage = renderer.renderImage(pageNumber, scaleFactor);
                else
                    pageImage = renderer.renderImage(pageNumber);
            }
            else
                return null;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return SwingFXUtils.toFXImage(pageImage, null);
    }


    public PDDocument getDocument() {
        return document;
    }
}
