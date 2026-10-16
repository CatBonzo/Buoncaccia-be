package it.buonacaccia.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import it.buonacaccia.model.FormQuestion;
import it.buonacaccia.model.FormSection;
import it.buonacaccia.model.QuestionType;
import it.buonacaccia.v1.dto.FieldErrorDTO;

/** Controlli sul modello di scheda e sulle risposte (feature 007, FR-001, FR-003, FR-005). */
public final class FormRules {

    public static final int MAX_QUESTIONS = 60;
    public static final int MAX_ANSWER_LENGTH = 2000;
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,40}");

    private FormRules() {
    }

    public static List<FormQuestion> questionsOf(List<FormSection> sections) {
        List<FormQuestion> all = new ArrayList<>();
        if (sections != null) {
            sections.forEach(s -> all.addAll(s.getQuestions() == null ? List.of() : s.getQuestions()));
        }
        return all;
    }

    /** Almeno una domanda e al massimo 60, id validi e unici, scelte con almeno due opzioni. */
    public static List<FieldErrorDTO> validateTemplate(List<FormSection> sections) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        int total = questionsOf(sections).size();
        if (total == 0 || total > MAX_QUESTIONS) {
            errors.add(new FieldErrorDTO("sections", "Servono da 1 a " + MAX_QUESTIONS + " domande in tutto."));
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < (sections == null ? 0 : sections.size()); i++) {
            List<FormQuestion> questions = sections.get(i).getQuestions() == null ? List.of() : sections.get(i).getQuestions();
            for (int j = 0; j < questions.size(); j++) {
                FormQuestion question = questions.get(j);
                String path = "sections[" + i + "].questions[" + j + "]";
                if (question.getId() == null || !ID.matcher(question.getId()).matches()) {
                    errors.add(new FieldErrorDTO(path + ".id", "L'identificativo usa lettere minuscole, cifre, _ e -, al massimo 40."));
                } else if (!seen.add(question.getId())) {
                    errors.add(new FieldErrorDTO(path + ".id", "L'identificativo è già usato in questa scheda."));
                }
                if (question.getText() == null || question.getText().isBlank()) {
                    errors.add(new FieldErrorDTO(path + ".text", "Scrivi il testo della domanda."));
                }
                if (question.getType() == null) {
                    errors.add(new FieldErrorDTO(path + ".type", "Indica il tipo della domanda."));
                } else if (question.getType() == QuestionType.CHOICE
                        && (question.getOptions() == null || question.getOptions().stream().distinct().count() < 2)) {
                    errors.add(new FieldErrorDTO(path + ".options", "Una scelta ha almeno due opzioni diverse."));
                }
            }
        }
        return errors;
    }

    /** Risposte del tipo giusto a domande che esistono; una risposta vuota è una domanda non risposta. */
    public static List<FieldErrorDTO> validateAnswers(List<FormQuestion> questions, Map<String, String> answers) {
        List<FieldErrorDTO> errors = new ArrayList<>();
        Map<String, FormQuestion> byId = new HashMap<>();
        questions.forEach(q -> byId.put(q.getId(), q));
        answers.forEach((id, value) -> {
            FormQuestion question = byId.get(id);
            String field = "answers." + id;
            if (question == null) {
                errors.add(new FieldErrorDTO(field, "Questa domanda non è nella scheda."));
            } else if (value != null && !value.isBlank()) {
                String problem = problem(question, value);
                if (problem != null) {
                    errors.add(new FieldErrorDTO(field, problem));
                }
            }
        });
        return errors;
    }

    /** Domande obbligatorie senza risposta. */
    public static List<FieldErrorDTO> missingRequired(List<FormQuestion> questions, Map<String, String> answers) {
        return questions.stream()
                .filter(FormQuestion::isRequired)
                .filter(q -> answers.get(q.getId()) == null || answers.get(q.getId()).isBlank())
                .map(q -> new FieldErrorDTO("answers." + q.getId(), "Risposta obbligatoria."))
                .toList();
    }

    private static String problem(FormQuestion question, String value) {
        if (value.length() > MAX_ANSWER_LENGTH) {
            return "Al massimo " + MAX_ANSWER_LENGTH + " caratteri.";
        }
        switch (question.getType()) {
            case YES_NO:
                return "true".equals(value) || "false".equals(value) ? null : "Rispondi true o false.";
            case CHOICE:
                return question.getOptions() != null && question.getOptions().contains(value) ? null : "Scegli una delle opzioni.";
            case DATE:
                try {
                    LocalDate.parse(value);
                    return null;
                } catch (DateTimeParseException e) {
                    return "Data non valida: usa aaaa-mm-gg.";
                }
            default:
                return null;
        }
    }
}
