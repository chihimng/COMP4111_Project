import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Book {
    @JsonProperty("Title")
    public String title;

    @JsonProperty("Author")
    public String author;

    @JsonProperty("Publisher")
    public String publisher;

    @JsonProperty("Year")
    public Integer year;

    Book() {}

    Book(String title, String author, String publisher, int year) {
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.year = year;
    }

    @JsonIgnore
    public boolean isValid() {
        return this.title != null && !this.title.isEmpty() &&
                this.author != null && !this.author.isEmpty() &&
                this.publisher != null && !this.publisher.isEmpty() &&
                this.year >= 0 && this.year <= 65535;
    }
}