/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import dao.ResultDao;
import dao.DatabaseOperationException;
import model.Department;
import model.Exam;
import model.Student;
import model.Subject;
import dao.ExamStatistics;

import dao.DuplicateResultException;
import dao.ExamNotFoundException;
import dao.InvalidMarkException;
import dao.StudentNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Result;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import dao.ExamService;
import dao.HibernateUtil;
import static org.junit.Assert.*;

/**
 * Key rules with the real database (skipped when PostgreSQL is not running):
 * missing student, missing exam, out-of-range marks, duplicate result.
 *
 * @author jeremie-tuyisenge
 */
public class DatabaseRulesTest {

    private ExamService service = new ExamService();

    @BeforeClass
    public static void databaseMustBeAvailable() {
        boolean available;
        try {
            available = HibernateUtil.getSessionFactory() != null;
        } catch (Throwable ex) {
            available = false;
        }
        Assume.assumeTrue("PostgreSQL is not available, the database tests are skipped", available);
    }

    @Before
    public void createTestData() throws Exception {
        cleanUp();
        service.registerDepartment("TD", "Test Department");
        service.registerSubject("TS", "Test Subject", "3");
        service.registerStudent("TST", "Test Student", "test@mail.com", "TD");
        service.registerExam("TEX", "Test Exam", "2026-11-15", "TS");
    }

    @After
    public void cleanUp() throws Exception {
        for (Result r : service.listResults()) {
            if (r.getStudent().getStudentId().startsWith("TS") && r.getStudent().getStudentId().length() == 3 || r.getExam().getExamId().equals("TEX")) {
                service.deleteResult(String.valueOf(r.getResultId()));
            }
        }
        try { service.deleteExam("TEX"); } catch (Exception ignore) { }
        try { service.deleteStudent("TST"); } catch (Exception ignore) { }
        try { service.deleteStudent("TS2"); } catch (Exception ignore) { }
        try { service.deleteSubject("TS"); } catch (Exception ignore) { }
        try { service.deleteDepartment("TD"); } catch (Exception ignore) { }
    }

    @Test
    public void validResultIsSavedWithItsGrade() throws Exception {
        Result saved = service.registerResult("TST", "TEX", "85.5");
        assertNotNull(saved.getResultId());
        assertEquals("A", saved.getGrade());
        assertEquals("A", service.findResult(String.valueOf(saved.getResultId())).getGrade());
    }

    @Test(expected = DuplicateResultException.class)
    public void duplicateResultIsRefused() throws Exception {
        service.registerResult("TST", "TEX", "70");
        service.registerResult("TST", "TEX", "90");
    }

    @Test(expected = StudentNotFoundException.class)
    public void missingStudentIsRefused() throws Exception {
        service.registerResult("NOSTUDENT", "TEX", "70");
    }

    @Test(expected = ExamNotFoundException.class)
    public void missingExamIsRefused() throws Exception {
        service.registerResult("TST", "NOEXAM", "70");
    }

    @Test(expected = InvalidMarkException.class)
    public void outOfRangeMarksAreRefusedAndNothingIsSaved() throws Exception {
        try {
            service.registerResult("TST", "TEX", "101");
        } finally {
            assertTrue(service.listResultsByStudent("TST").isEmpty());
        }
    }

    @Test
    public void resultsCanBeListedByStudent() throws Exception {
        service.registerResult("TST", "TEX", "55");
        List<Result> list = service.listResultsByStudent("TST");
        assertEquals(1, list.size());
        assertEquals("D", list.get(0).getGrade());
    }

    // ---------- multi-step operation (atomic) ----------
    @Test
    public void bulkEntrySavesEveryResultTogether() throws Exception {
        service.registerStudent("TS2", "Second Student", "two@mail.com", "TD");
        Map<String, String> marks = new LinkedHashMap<String, String>();
        marks.put("TST", "90");
        marks.put("TS2", "45");
        List<Result> saved = service.registerResultsForExam("TEX", marks);
        assertEquals(2, saved.size());
        assertEquals("A", service.listResultsByStudent("TST").get(0).getGrade());
        assertEquals("F", service.listResultsByStudent("TS2").get(0).getGrade());
    }

    @Test
    public void bulkEntryWithOneBadMarkSavesNothing() throws Exception {
        service.registerStudent("TS2", "Second Student", "two@mail.com", "TD");
        Map<String, String> marks = new LinkedHashMap<String, String>();
        marks.put("TST", "90");      // good
        marks.put("TS2", "150");     // out of range
        try {
            service.registerResultsForExam("TEX", marks);
            fail("The out of range mark must be refused");
        } catch (InvalidMarkException expected) {
            // good
        }
        assertTrue("the good result must not stay in the database", service.listResultsByStudent("TST").isEmpty());
    }

    @Test
    public void databaseFailureInsideTheTransactionRollsEverythingBack() throws Exception {
        service.registerStudent("TS2", "Second Student", "two@mail.com", "TD");
        Student s1 = service.findStudent("TST");
        Student s2 = service.findStudent("TS2");
        Exam exam = service.findExam("TEX");
        List<Result> two = new ArrayList<Result>();
        two.add(new Result(s1, exam, new java.math.BigDecimal("70"), "B"));
        two.add(new Result(s1, exam, new java.math.BigDecimal("71"), "B"));   // same student + exam twice: database refuses
        try {
            new dao.ResultDao().saveAll(two);
            fail("The database must refuse the duplicate");
        } catch (DatabaseOperationException expected) {
            // good
        }
        assertTrue("the first insert must be rolled back", service.listResultsByStudent("TST").isEmpty());
    }

    // ---------- streams ----------
    @Test
    public void statisticsAreCalculatedWithStreams() throws Exception {
        service.registerStudent("TS2", "Second Student", "two@mail.com", "TD");
        service.registerResult("TST", "TEX", "90");
        service.registerResult("TS2", "TEX", "40");
        ExamStatistics stats = service.statisticsForExam("TEX");
        assertEquals(2, stats.getCount());
        assertEquals(65.0, stats.getAverage(), 0.001);
        assertEquals(90.0, stats.getHighest(), 0.001);
        assertEquals(1, stats.getPassed());
        assertEquals(Long.valueOf(1), stats.getGradeCounts().get("A"));
    }
}
