package it.buonacaccia.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import it.buonacaccia.model.FormQuestion;
import it.buonacaccia.model.FormSection;
import it.buonacaccia.model.QuestionType;
import it.buonacaccia.v1.dto.FieldErrorDTO;
import org.junit.jupiter.api.Test;

class FormRulesTest {

    private static FormQuestion q(String id, QuestionType type, boolean required, String... options) {
        return FormQuestion.builder().id(id).text("Domanda " + id).type(type).required(required)
                .options(options.length == 0 ? null : List.of(options)).build();
    }

    private static List<FormSection> sections(FormQuestion... questions) {
        return List.of(FormSection.builder().title("Sezione").questions(List.of(questions)).build());
    }

    private static List<String> fields(List<FieldErrorDTO> errors) {
        return errors.stream().map(FieldErrorDTO::getField).toList();
    }

    @Test
    void unModelloValidoNonHaErrori() {
        assertThat(FormRules.validateTemplate(sections(q("fede", QuestionType.LONG_TEXT, false),
                q("scelta", QuestionType.CHOICE, true, "a", "b")))).isEmpty();
    }

    @Test
    void ilModelloVuoto_gliIdDoppiEleScelteSenzaOpzioniSonoRifiutati() {
        assertThat(fields(FormRules.validateTemplate(List.of()))).containsExactly("sections");
        assertThat(fields(FormRules.validateTemplate(sections(q("a", QuestionType.TEXT, false), q("a", QuestionType.TEXT, false)))))
                .containsExactly("sections[0].questions[1].id");
        assertThat(fields(FormRules.validateTemplate(sections(q("scelta", QuestionType.CHOICE, false, "una")))))
                .containsExactly("sections[0].questions[0].options");
        assertThat(fields(FormRules.validateTemplate(sections(q("Id Strano", QuestionType.TEXT, false)))))
                .containsExactly("sections[0].questions[0].id");
    }

    @Test
    void nonSiSuperanoLeSessantaDomande() {
        FormQuestion[] many = new FormQuestion[61];
        for (int i = 0; i < many.length; i++) {
            many[i] = q("d" + i, QuestionType.TEXT, false);
        }
        assertThat(fields(FormRules.validateTemplate(sections(many)))).containsExactly("sections");
    }

    @Test
    void leRisposteRispettanoIlTipoDellaDomanda() {
        List<FormQuestion> questions = FormRules.questionsOf(sections(q("t", QuestionType.TEXT, false),
                q("sn", QuestionType.YES_NO, false), q("c", QuestionType.CHOICE, false, "a", "b"),
                q("d", QuestionType.DATE, false)));

        assertThat(FormRules.validateAnswers(questions, Map.of("t", "ciao", "sn", "true", "c", "a", "d", "2026-10-15"))).isEmpty();
        assertThat(fields(FormRules.validateAnswers(questions, Map.of("sn", "forse", "c", "z", "d", "ieri", "x", "1"))))
                .containsExactlyInAnyOrder("answers.sn", "answers.c", "answers.d", "answers.x");
        assertThat(fields(FormRules.validateAnswers(questions, Map.of("t", "x".repeat(2001)))))
                .containsExactly("answers.t");
    }

    @Test
    void all_invioLeObbligatorieVannoRisposte() {
        List<FormQuestion> questions = FormRules.questionsOf(sections(q("a", QuestionType.TEXT, true),
                q("b", QuestionType.TEXT, false), q("c", QuestionType.YES_NO, true)));

        assertThat(fields(FormRules.missingRequired(questions, Map.of("a", "  ")))).containsExactly("answers.a", "answers.c");
        assertThat(FormRules.missingRequired(questions, Map.of("a", "x", "c", "false"))).isEmpty();
    }
}
