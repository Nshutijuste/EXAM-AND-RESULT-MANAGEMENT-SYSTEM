/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package test;

import dao.Grade;

import dao.InvalidMarkException;
import dao.ValidationException;
import java.math.BigDecimal;
import org.junit.Test;
import dao.ExamService;
import static org.junit.Assert.*;

/**
 * Rules that do not need the database.
 *
 * @author jeremie-tuyisenge
 */
public class ServiceRulesTest {

    @Test(expected = InvalidMarkException.class)
    public void negativeMarksAreRefused() throws Exception {
        ExamService.validateMarks("-1");
    }

    @Test(expected = InvalidMarkException.class)
    public void marksAbove100AreRefused() throws Exception {
        ExamService.validateMarks("100.5");
    }

    @Test(expected = InvalidMarkException.class)
    public void marksThatAreNotNumbersAreRefused() throws Exception {
        ExamService.validateMarks("5d");
    }

    @Test
    public void marksFrom0To100AreAccepted() throws Exception {
        assertEquals(new BigDecimal("0.00"), ExamService.validateMarks("0"));
        assertEquals(new BigDecimal("100.00"), ExamService.validateMarks("100"));
        assertEquals(new BigDecimal("85.50"), ExamService.validateMarks(" 85.5 "));
    }

    @Test
    public void gradeFollowsTheMarks() {
        assertEquals("A", ExamService.calculateGrade(new BigDecimal("80")));
        assertEquals("B", ExamService.calculateGrade(new BigDecimal("79.99")));
        assertEquals("C", ExamService.calculateGrade(new BigDecimal("60")));
        assertEquals("D", ExamService.calculateGrade(new BigDecimal("50")));
        assertEquals("F", ExamService.calculateGrade(new BigDecimal("49.99")));
    }

    @Test(expected = ValidationException.class)
    public void impossibleDateIsRefused() throws Exception {
        ExamService.parseDate("2026-02-31");
    }

    @Test(expected = ValidationException.class)
    public void badEmailIsRefused() throws Exception {
        ExamService.checkEmail("john.at.mail");
    }

    @Test(expected = ValidationException.class)
    public void shortNameIsRefused() throws Exception {
        ExamService.checkText("ab", "Full name", 3, 50);
    }

    @Test
    public void gradeEnumHasTheScaleAndOnlyFFails() {
        assertEquals(Grade.A, Grade.fromMarks(new java.math.BigDecimal("80")));
        assertEquals(Grade.F, Grade.fromMarks(new java.math.BigDecimal("49.99")));
        assertTrue(Grade.D.isPass());
        assertFalse(Grade.F.isPass());
    }
}
